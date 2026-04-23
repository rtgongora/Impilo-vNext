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

**PII** (`PiiAccessLevel`) and **clinical** (`ClinicalAccessLevel`) are **orthogonal** in the contract; both must be honoured at the PEP.

## Resolving visibility (`x-obligations` + flat headers)

- **`VisibilityHeaderParser.resolve(request, objectMapper)`** — when an `ObjectMapper` is provided, reads **`x-obligations`** JSON and extracts nested **`visibilityProfile`**. **Non-null** JSON fields **overlay** flat trust headers (`x-visibility-tier`, `x-export-policy`, …), so Envoy can mirror coarse headers while the PDP supplies a partial profile. If obligations are absent or invalid, resolution uses **flat** headers only. With **`mapper == null`**, only flat headers are read.
- **`TrustContextFilter`** accepts an optional **`ObjectMapper`** constructor argument. **`reporting-service`** registers `new TrustContextFilter(objectMapper)` so **`VisibilityContextHolder`** reflects obligations-based profiles on every request, not only flat headers.

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
