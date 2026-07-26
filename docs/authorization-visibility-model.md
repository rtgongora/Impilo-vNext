# Authorization, data classification, and visibility (Impilo vNext)

This document describes the **wave-1** cross-service model implemented on the `claude/staging-ux-orchestration-remediation-Yypyl` line of work: **RBAC + ABAC policy rules + explicit data visibility / representation** with **workflow-gated escalation** audited through Tshepo.

## Layers

1. **RBAC (functional role)** — Keycloak realm roles and/or `policy_rule.role`; defines *broad function*, not visibility.
2. **ABAC** — `policy_rule` JSONB `conditions`, including optional `visibility` overlay (see below).
3. **Purpose-of-use** — `PurposeOfUse` drives a **default visibility envelope** in `VisibilityObligationComposer`.
4. **Resource sensitivity** — `ResourceSensitivityClassifier` caps disclosure using `resource_type` (URL-derived in ext_authz).
5. **Representation (PEP)** — Downstream services read flat headers + `VisibilityContextHolder` / `VisibilityHeaderParser` and shape responses (`ProviderRepresentation`, `FacilityRepresentation`, guards on PCT/Vito).

## Visibility tiers (`DataVisibilityTier`)

| Tier | Meaning |
|------|---------|
| `AGGREGATE_ONLY` | Counts / summaries only; row-level person payloads denied at PEP. |
| `DEIDENTIFIED_ROW_LEVEL` | Row-level without direct identifiers. |
| `PSEUDONYMISED_PERSON_LEVEL` | Longitudinal pseudonymous linkage, no direct IDs. |
| `IDENTIFIED_OPERATIONAL_ONLY` | Identifiers for operations (scheduling, logistics, contact). |
| `IDENTIFIED_LIMITED_CLINICAL` | Identifiers + restricted clinical depth. |
| `FULL_IDENTIFIED_CLINICAL` | Full clinical (treatment / controlled escalation). |
| `SPECIALLY_PROTECTED_CLINICAL` | Full clinical **plus** content classified `SPECIALLY_PROTECTED`. Strictly above the tier below it. **No purpose-of-use grants it** — see the confidentiality control below. |

**PII** (`PiiAccessLevel`) and **clinical** (`ClinicalAccessLevel`) are **orthogonal** in the contract; both must be honoured at the PEP.

## The specially-protected confidentiality control (Step 4.7)

`SPECIALLY_PROTECTED` covers restricted adolescent and safeguarding data, sexual and reproductive health, HIV, mental health and gender-based violence. It is the only class with its own enforcement step, because it is the only one where a *label without teeth is worse than no label*: a record that looks protected in the schema and in the UI, but is readable by exactly the same people, manufactures a false assurance for the clinician deciding whether it is safe to write something down and for the adolescent told their record is confidential.

**The control splits across three layers, and each half is useless alone.**

| Layer | Decides | Where |
|---|---|---|
| Terminology SoR | *which content* is confidential by nature | zibo governed ValueSet + `POST /internal/v1/confidentiality/classify` (migration `V008`) |
| PDP | *whether this requester* may receive protected content | `PolicyEngine` Step 4.7 + `ResourceSensitivityClassifier` |
| PEP (service) | *which records in this response* to withhold | `SpeciallyProtectedVisibilityGuard` (shared-core) |

The PDP sees a URL, never a record, so it cannot know that row 7 of a collection is a safeguarding note. The service knows that but must not invent its own access rule. Each layer assuming the other did the work is precisely why the class was inert for so long.

### The confidential lane

Any route serving protected content **must** carry a lane marker — `confidential`, `safeguarding` or `protected-disclosure` — in its path. `ResourceSensitivityClassifier` recognises the marker *before* the ordinary clinical branch, which would otherwise claim `confidential-encounters` on the substring `encounter` and silently downgrade it. Markers are deliberately unambiguous: a control that fires on ordinary care gets routed around, which protects nobody.

### Entitlement

Default is **withheld**. Nothing in the purpose-of-use envelope reaches `SPECIALLY_PROTECTED_CLINICAL`, so an actor must be granted it. Within the confidential lane, in order:

1. **A delegated act is refused absolutely** (`PROTECTED_RECORD_DELEGATE_DENIED`). A guardian or caregiver acting on another person's behalf — `X-Subject-ID` ≠ actor with an ACTIVE Mvumo delegation — never reaches protected content. No policy rule widens this; if the governance channel could, the hole would reopen through a seed.
2. **The subject themselves is entitled** (`SUBJECT_SELF`). Deliberately established in code, not by a rule overlay: an overlay would hand the tier to every actor matching the route, including one who arrived at someone else's record.
3. **Otherwise a governed rule grant is required** (`PROTECTED_RECORD_NOT_ENTITLED` if absent) — a `policy_rule` whose `conditions.visibility.visibilityTier` is `SPECIALLY_PROTECTED_CLINICAL` (seeded in `V048`). A clinical role alone is not enough; if it were, the class would again mean nothing.
4. **Verified break-glass reaches protected content.** It already requires an active break-glass request plus a completed step-up, and is fully audited. A bare `EMERGENCY` purpose header does **not** — that is an unverified claim.

> **Guardian linkage lives in two places, and the distinction is load-bearing.** VITO's `ClientRelationshipEntity` records the *family relationship* (`GUARDIAN_OF`, `DEPENDENT_OF`, `CAREGIVER_OF`, `NEXT_OF_KIN`, `PROXY_ACCESS_FOR`). Mvumo's `delegation_relationship` is the *act-of-record for acting on another person's behalf*. The PDP resolves Mvumo, never VITO: a relationship is not an authorisation.

### Audit

Both outcomes emit a dedicated governance event — `CONFIDENTIAL_ACCESS_GRANTED` and `CONFIDENTIAL_ACCESS_REFUSED` — via the transactional outbox, so the control can be reviewed without filtering the whole authz decision stream. The grant event is driven off the **composed** visibility tier rather than the entitlement flag, so any future route that can reach protected data lands in the stream even if nobody remembers to instrument it.

### PEP obligations

`SpeciallyProtectedVisibilityGuard` **fails closed** on a missing profile, unlike `AggregateVisibilityGuard` and `ClinicalVisibilityGuard`, which treat absence as permissive so unwired services keep working. Silence from the PDP must not mean disclosure here. Withheld records should be answered exactly as a non-existent record is: a 403 distinguishable from a 404 tells the guardian the confidential record is there, which is most of what confidentiality was protecting.

Rego mirror: `infra/opa/impilo/confidentiality.rego` (shadow only; Java stays authoritative).

## Resolving visibility (`x-obligations` + flat headers)

- **`VisibilityHeaderParser.resolve(request, objectMapper)`** — when an `ObjectMapper` is provided, reads **`x-obligations`** JSON and extracts nested **`visibilityProfile`**. **Non-null** JSON fields **overlay** flat trust headers (`x-visibility-tier`, `x-export-policy`, …), so Envoy can mirror coarse headers while the PDP supplies a partial profile. If obligations are absent or invalid, resolution uses **flat** headers only. With **`mapper == null`**, only flat headers are read.
- **`TrustContextFilter`** accepts an optional **`ObjectMapper`** constructor argument. Platform **`SecurityConfig`** beans register **`new TrustContextFilter(objectMapper)`** (Spring’s primary **`ObjectMapper`**) so **`VisibilityContextHolder`** reflects **`x-obligations`** on every request, not only flat headers.

## Trust headers (Tshepo → Envoy → service)

Declared in `TrustHeaders` and mirrored into responses by `PolicyEngine.buildHeaderMutations`:

- `x-visibility-tier`, `x-pii-access`, `x-clinical-access`, `x-aggregate-only`
- `x-resource-sensitivity`, `x-escalation-grant-id`, `x-export-policy`
- `x-suppress-fields`, `x-drill-down-allowed`
- `x-obligations` JSON (includes nested `visibilityProfile`)

## Policy rule ABAC overlay

Optional `conditions.visibility` JSON on `policy_rule`:

```json
{
  "min_loa": 2,
  "visibility": {
    "visibilityTier": "AGGREGATE_ONLY",
    "piiAccess": "NONE",
    "clinicalAccess": "NONE",
    "aggregateOnly": true,
    "exportPolicy": "AGGREGATE_ONLY"
  }
}
```

This allows **district / programme supervisors** to be ALLOWed on a resource while remaining **aggregate-only** at the representation layer.

## Workflow escalation

- Tables: `tshepo_authz.visibility_escalation_request`, `visibility_escalation_grant` (Flyway `V003`).
- API (`tshepo-authz-service`, JWT required except ext_authz):
  - `POST /v1/visibility-escalations/requests` — create `PENDING` request (`x-tenant-id`, `x-actor-id`).
  - `GET /v1/visibility-escalations/requests/pending`
  - `POST /v1/visibility-escalations/requests/{id}/review` — approver roles from `tshepo.authz.visibility-escalation-approver-roles`; issues **grant token**.
- Client sends `x-escalation-grant-id` on subsequent calls; `PolicyEngine` validates grant then **raises** visibility up to the grant ceiling inside `VisibilityProfile.Builder.liftWithEscalation`.
- Governance events: `ESCALATION_REQUESTED`, `ESCALATION_APPROVED`, `ESCALATION_DENIED` via `AuditPublisher.queueGovernanceEvent` (same Kafka topic as authz stream).

## PEP integration (selected services)

| Service | Behaviour |
|---------|-----------|
| **varapi** | `GET .../providers/{id}` → 403 if aggregate-only; else `ProviderRepresentation` masks PII for MASKED/NONE. |
| **tuso** | `GET .../facilities/{id}` → 403 aggregate-only; else `FacilityRepresentation` strips contacts / address when PII masked. |
| **vito** | Aggregate-only → empty `listClients`; `getClient` → 403. |
| **pct** | `getEncounter`, patient `timeline` → 403 when clinical access denied. |
| **indawo** | `getSiteInternal` parses flat headers (no `TrustContextFilter`); aggregate 403; `SiteRepresentation` clears address when PII masked. |
| **reporting** | `POST /internal/v1/reports/{key}/run` resolves visibility via **`resolve(...)`** (obligations + headers). **403** `EXPORT_VISIBILITY_DENIED` when `ExportVisibilityGuard.deniesReportRun` fires. When **`ExportPolicy.REDACTED`** (or non-empty **`suppressFields`**), runs succeed but **`ExportReportOutputRedactor`** masks JSON rows (**`JsonRepresentationShaper`**) and sensitive **CSV** columns (header heuristic). |
| **fhir-gateway** | `POST /internal/v1/gateway/forward` → **403** `VISIBILITY_CLINICAL_BLOCKED` for clinical FHIR resource types on read/search-style operations when `ClinicalVisibilityGuard` blocks. |
| **experience-bff** | `GET /internal/v1/profile/visibility` — uses **`resolve`** with Jackson; returns **`obligationsHeaderPresent`**, **`suppressFields`**, and **`source`** (`obligations-or-headers` vs `headers-absent`). |
| **experience UI** | `VisibilityContextBar` + `useVisibilityProfile` show the active tier / PII / clinical / export posture at the bottom of the shell when authenticated. |

## Sample policy seeds (Flyway `V004`)

`V004__seed_sample_visibility_policy_rules.sql` inserts **documentation placeholder** rules for tenant `00000000-0000-0000-0000-000000000001`:

- **DISTRICT_SUPERVISOR** on `facilities` — aggregate-only, no PII/clinical, aggregate export only.
- **PROVINCIAL_COORDINATOR** on `reports` — de-identified row-level, masked PII, clinical summary cap, redacted export.
- **ENVIRONMENTAL_HEALTH_OFFICER** on `sites` — identified operational, **no clinical**, redacted export.

Copy or adapt `tenant_id` and role names for each deployment.

## Manual testing

1. Run `tshepo-authz-service` with Postgres + Flyway; confirm `V003`–`V004` applied.
2. Obtain JWT; `POST /v1/visibility-escalations/requests` with body  
   `{"workflowType":"QUALITY_REVIEW","justification":"demo","requestedVisibilityCeiling":"PSEUDONYMISED_PERSON_LEVEL"}`.
3. `POST /v1/visibility-escalations/requests/{id}/review` with approver role and `{"approved":true}` — capture `grantToken`.
4. Call ext_authz (HTTP or gRPC simulation) with mandatory trust headers + `x-escalation-grant-id: <token>`; verify `x-visibility-tier` reflects lift within resource cap.
5. Call Varapi `GET /v1/internal/providers/{id}` with `x-visibility-tier: AGGREGATE_ONLY` → expect **403** `VISIBILITY_AGGREGATE_ONLY`.
6. Call with `RESEARCH` purpose through Tshepo; expect masked provider names on Varapi.

## PDP testing note

`PolicyEngine` depends on `DeviceRiskScoreEvaluator` (implemented by `RiskScoring`) so unit tests can stub **interface** mocks without relying on Mockito inline mocking of concrete classes on newer JDKs.

`tshepo-authz-service` ships `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` selecting **SubclassByteBuddyMockMaker** so `mvn test` remains usable on **JDK 22+** where inline instrumentation is unreliable. Other modules (for example `reporting-service`) still assume **JDK 21** in CI (`JAVA_VERSION: '21'` in `.github/workflows/ci.yml`).

## Known gaps / next wave

- **Full PEP coverage** for all clinical/commerce/export paths; many controllers still trust-only.
- **Companion / Indawo** — propagate visibility via gateway to set same headers as Envoy+Tshepo path.
- **tshepo-service** HTTP PDP remains a second implementation; align or deprecate.
- **UI** — route aggregate-only users to dedicated aggregate workspaces instead of only the context bar.
- **FHIR** — extend visibility checks to Bundle transactions and write operations with explicit action metadata.
- **Reporting** — redacted CSV generation when `ExportPolicy.REDACTED` instead of only hard deny.
- **Performance** — cache parsed `VisibilityProfile` per request if JSON obligations grow.
