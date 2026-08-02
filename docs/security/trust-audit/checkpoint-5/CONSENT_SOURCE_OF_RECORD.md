# Consent source of record — Checkpoint 5 designation

**Captured:** 2026-08-02 · **Branch:** `claude/tshepo-trust-completion-Yypyl`
**Closes:** the ownership question Checkpoint 1 left explicitly open
(`docs/security/trust-audit/checkpoint-1/CONSENT_CONTRACT_INCOMPATIBILITY.md` §"Unresolved
ownership"), which forbade Checkpoint 2's compatibility adapters from resolving it silently.

**Doctrine basis:** `docs/doctrine/tshepo-trust-plane-doctrine.md` §4 (Component Responsibility
Matrix — `tshepo-consent-service` = "System-of-record for consent and lawful-basis
grant/revocation state"; Mvumo = "the governed consent & lawful-basis experience … **must not**
become a second source of truth for grant state") and §7 ("Mvumo owns the experience; **one
designated source** owns authoritative grant/revocation state").

---

## 1. The designation

> **`tshepo-consent-service` — schema `tshepo_consent`, table `consent_directive` — is the
> authoritative system of record for consent and lawful-basis GRANT and REVOCATION state
> across the estate.**
>
> Every other consent-bearing store is one of: an **experience/orchestration** store that
> materialises into it, a **domain workflow artefact** that references it, a **derived
> cache**, a **different concept entirely**, or **legacy pending retirement**. None of them is
> authoritative for whether a person has granted or withdrawn consent.

This designation is about **grant state**. It is deliberately **not** a claim that consent is
enforced on the live request path — it is not. See §6.

---

## 2. Evidence per store

Inventory produced mechanically from `services/*/src/main/resources/**/*.sql` (every
`CREATE TABLE` whose schema-or-table reference contains `consent`), then attributed to code by
grep. The same extraction is the input to the guard in §5.

### 2.1 `tshepo-consent-service` — **SYSTEM OF RECORD**

| Facet | Evidence |
|---|---|
| DB / schema | `tshepo_consent` |
| Migration | `services/tshepo-consent-service/src/main/resources/db/migration/V001__init.sql` |
| Tables | `tshepo_consent.consent_directive` (FHIR R4 Consent as `fhir_consent_json JSONB`), `tshepo_consent.consent_audit`, `tshepo_consent.share_link`, `tshepo_consent.event_outbox` |
| Entity | `services/tshepo-consent-service/src/main/java/zw/gov/mohcc/impilo/tshepo/consent/persistence/ConsentDirectiveEntity.java` (`@Table(name = "consent_directive", schema = "tshepo_consent")`) |
| Repository | `.../consent/persistence/ConsentDirectiveRepository.java` |
| Write path | `.../consent/api/ConsentController.java` (`@RequestMapping("/v1/consent")`; POST create, PUT `/{id}`, DELETE `/{id}` revoke) → `.../consent/service/ConsentCrudService.java` |
| Portal write path | `.../consent/api/PortalConsentController.java` |
| Evaluate engine | `.../consent/api/ConsentEvaluationController.java` — **GET** `/v1/consent/evaluate` → `.../consent/service/ConsentEvaluationService.java` |
| FHIR mapping | `.../consent/service/FhirConsentMapper.java` |
| Published contract | `contracts/openapi/tshepo-consent.openapi.yaml` |
| Revocation event | published on Kafka topic `platform.consent.events` (consumed by fhir-gateway, see §2.5) |

Why this one and not Mvumo: it holds the **directive** in the canonical interchange shape
(FHIR R4 Consent), it carries the immutable `consent_audit` chain, it owns the `event_outbox`
that broadcasts revocation, and it is the only consent store with an **evaluate** engine that
other planes call. Mvumo has none of those four.

### 2.2 `mvumo-service` — **consent EXPERIENCE and orchestration** (not a source of truth)

| Facet | Evidence |
|---|---|
| DB / schema | `mvumo` |
| Migration | `services/mvumo-service/src/main/resources/db/migration/V001__init_mvumo.sql` |
| Tables | `mvumo.consent_template`, `mvumo.consent_request`, `mvumo.remote_consent_session`, `mvumo.consent_event` |
| Entities | `.../mvumo/persistence/ConsentTemplateEntity.java`, `ConsentRequestEntity.java`, `RemoteConsentSessionEntity.java`, `ConsentEventEntity.java` (+ repositories) |
| Link to the SoR | `services/mvumo-service/src/main/resources/db/migration/V003__tshepo_link.sql` adds `mvumo.consent_request.tshepo_consent_id UUID`, commented *"UUID of the FHIR Consent directive in tshepo-consent-service when granted/partially granted."* |
| Materialisation client | `services/mvumo-service/src/main/java/zw/gov/mohcc/impilo/mvumo/integration/TshepoConsentClient.java` — *"materialises FHIR R4 Consent directives on grant, revokes on withdraw"*; `POST {mvumo.tshepo-consent.base-url}/v1/consent` |

**Role now:** Mvumo owns the *solicitation → presentation → capture → withdrawal* experience:
templates, requests, remote sessions, and the product-level event trail. On grant it **must**
materialise into `tshepo_consent.consent_directive` and record the returned id in
`tshepo_consent_id`. Mvumo rows are the record of *how consent was obtained*, never the
authority on *whether it currently holds*.

**Known weak points in that arrangement (recorded, not fixed here):**

- The materialisation is switchable: `mvumo.tshepo-consent.enabled` (default `true`). When
  false, `TshepoConsentClient.createDirective` logs a warning and **returns `null`** — a grant
  captured in Mvumo then never reaches the SoR, and nothing downstream fails. A flag that can
  silently de-designate the source of record is a defect against this designation.
- `POST /internal/v1/mvumo/evaluate` (`.../mvumo/api/MvumoInternalController.java:241`) is a
  **second evaluate surface**. Checkpoint 1 recorded it as DISCONNECTED from production callers
  (`MVUMO_CONSENT_CONTRACT_MAP.md`). It must not acquire production callers for
  clinical-consent decisions; `GET /v1/consent/evaluate` on the SoR is the one evaluate wire.
- `mvumo.legal_agreement` (V005) and `mvumo.delegation_relationship` (V006) are adjacent
  lawful-basis/authority concepts, out of scope for this designation and **not** consent
  directives.

### 2.3 `tshepo-service` (legacy monolith) — **LEGACY, retirement criterion in §4**

| Facet | Evidence |
|---|---|
| DB / schema | `tshepo` |
| Migration | `services/tshepo-service/src/main/resources/db/migration/V003__consent.sql` |
| Table | `tshepo.consent_directive` — a **second, differently shaped** directive table: `subject_cpid`, `grantor_id`, `grantee_scope`, `purpose_of_use`, `resource_scope`, `status`, `valid_from/valid_to` |
| Entity | `services/tshepo-service/src/main/java/zw/gov/mohcc/impilo/tshepo/persistence/ConsentDirectiveEntity.java` (`@Table(name = "consent_directive", schema = "tshepo")`) |
| Repository | `.../tshepo/persistence/ConsentDirectiveRepository.java` — one method, `existsActiveConsent(tenantId, subjectCpid, actorId, purpose)` |
| Sole reader | `services/tshepo-service/src/main/java/zw/gov/mohcc/impilo/tshepo/core/PolicyEngine.java` — Step 5 (`requiresConsent` at :400, `checkConsent` at :409, `consentRepo.existsActiveConsent` at :417) |

It is **not** FHIR-shaped, it has **no audit table**, it has **no outbox**, and it is not
reachable by any consent API. Retiring it is §4.

### 2.4 `experience-bff` — **a DIFFERENT THING; do not converge it**

> ⚠️ `policy_consent` is **privacy-policy / Terms-of-Use acceptance**. It is *not* a clinical
> consent directive and must **not** be merged into `tshepo_consent.consent_directive`.
> Anyone "converging consent stores" who folds this one in will destroy the ToU acceptance
> ledger and will not have moved a single clinical directive.

| Table | Migration | What it actually is |
|---|---|---|
| `policy_consent` | `services/experience-bff/src/main/resources/db/migration/V38__policy_consent.sql` | Versioned acceptance of `PRIVACY_POLICY` / `TERMS_OF_USE` per user, per `policy_version`, per `consent_channel` (`WEB`/`APP`/`SMS`/`USSD`/`KIOSK`/`VOICE`/`PAPER`/`OPERATOR`). Read by `.../experience/controller/PolicyConsentController.java`. **Legitimately BFF-owned** — it is a product/legal artefact of the shell, not clinical consent. |
| `consent_request` | same migration | Outbound **solicitation** of that policy acceptance over SMS/USSD/VOICE/EMAIL. Name-collides with `mvumo.consent_request`; unrelated concept. |
| `consent_preferences` | `V5__citizen_app_tables.sql` (re-declared `IF NOT EXISTS` in `V8__encounter_discharge_columns.sql`) | Per-patient category grant/revoke. **This one WAS a clinical-consent shadow.** It is now **ORPHANED**: both callers were repointed to the SoR — `.../controller/mobile/citizen/CitizenConsentController.java` and `.../CitizenProfileController.java` now call `.../client/TshepoConsentServiceClient.java`, with the old `jdbcTemplate` SQL left in place as comments. No live reader or writer remains in `services/experience-bff/src/main/`. |

`consent_preferences` is therefore a table that exists but is dead. It is kept in the guard
allowlist because deleting a table that may hold historical rows is a data decision, not a
tidy-up (see `docs/` law: removing capability to hide incompleteness is forbidden). Its
retirement rides on the same evidence bar as §4: prove no rows of value, or migrate them.

### 2.5 `fhir-gateway-service` — **derived cache, no table**

- `services/fhir-gateway-service/src/main/java/zw/gov/mohcc/impilo/fhirgateway/events/ConsentCacheService.java`
  — an in-memory `ConcurrentHashMap` with a 5-minute TTL. **Not a store**; no migration, no
  table, nothing survives a restart.
- Fed by `.../events/ConsentRevocationConsumer.java`, Kafka topic `platform.consent.events`
  (`TOPIC` at :20), i.e. sourced *from* the SoR.
- Synchronous path: `.../core/ConsentEnforcementService.java` + `.../config/ConsentClientConfig.java`
  call the SoR's **GET** `/v1/consent/evaluate` — Checkpoint 1 recorded this consumer as the
  *correct* one.

This is the shape a non-authoritative consent consumer should have: a TTL'd derivation of the
SoR plus a synchronous call to it, never its own table.

### 2.6 Narrow domain stores — workflow artefacts, correctly local

| Table | Migration | Why it is not a competing SoR |
|---|---|---|
| `inpatient.procedure_consent` | `services/inpatient-service/src/main/resources/db/migration/V025__procedure_consent_bundle.sql` | Per-episode checklist of `PROCEDURE`/`ANAESTHESIA`/`TRANSFUSION` consent required before a procedure. It carries `mvumo_consent_request_id UUID` — it **references** the consent journey rather than restating grant state. Its `status` is a theatre-readiness gate, not a lawful basis. |
| `simba.simba_consent_preference` | `services/simba-service/src/main/resources/db/migration/V007__wellness_assessment_risk_timeline_followups.sql` | Wellness-continuum sharing *preferences* (caregiver/provider/programme involvement, `data_sharing_scope`, sensitive categories). Peer-wellness plane, per `docs/doctrine/care-continuum-doctrine.md`; not a clinical consent directive. |

**UNKNOWN (honest):** neither of these has been proven to *reconcile* with the SoR. Inpatient
holds an `mvumo_consent_request_id` but nothing in this audit verified that a withdrawal in
the SoR flips `inpatient.procedure_consent.withdrawn`. Simba's preference has no link column
to the SoR at all. Both are recorded as open reconciliation questions, not as defects closed.

---

## 3. Role of each store after this designation

| Store | Role | May hold authoritative grant state? |
|---|---|---|
| `tshepo_consent.consent_directive` | **System of record** — grant, revocation, evaluate | **YES — the only one** |
| `tshepo_consent.consent_audit` | Immutable mutation trail for the SoR | n/a (evidence) |
| `mvumo.consent_*`, `mvumo.remote_consent_session` | Consent experience + orchestration; materialises into the SoR | NO |
| `tshepo.consent_directive` (monolith) | **Legacy** — retirement criterion in §4 | NO |
| `experience-bff.policy_consent` / `consent_request` | Privacy-policy & ToU acceptance — **a different concept** | n/a (not clinical consent) |
| `experience-bff.consent_preferences` | Orphaned former shadow; callers repointed to the SoR | NO |
| `fhir-gateway` consent cache | Derived, in-memory, TTL'd from `platform.consent.events` | NO |
| `inpatient.procedure_consent` | Procedure-readiness workflow artefact referencing the journey | NO |
| `simba.simba_consent_preference` | Wellness-plane sharing preference | NO |

---

## 4. Retirement criterion for `tshepo.consent_directive` (legacy)

**This is a retirement criterion, not a deletion.** Nothing here removes the table, the entity,
the repository, or `PolicyEngine` Step 5. Removing a consent check to make a convergence look
clean would delete a control and hide the incompleteness; that is forbidden.

### 4.1 Who still reads it — the complete current list

Grep over `services/tshepo-service/src/` for `consent_directive` / `ConsentDirective`:

| Reader | Path |
|---|---|
| `PolicyEngine` Step 5 | `services/tshepo-service/src/main/java/zw/gov/mohcc/impilo/tshepo/core/PolicyEngine.java` — `requiresConsent` (:400) → `checkConsent` (:409) → `consentRepo.existsActiveConsent` (:417) |
| Repository | `.../tshepo/persistence/ConsentDirectiveRepository.java` |
| Entity | `.../tshepo/persistence/ConsentDirectiveEntity.java` |
| Schema | `.../db/migration/V003__consent.sql` |

**No other service reads it.** No UI reads it.

### 4.2 Who writes it — nobody in application code

Estate-wide grep for `tshepo.consent_directive` writes returns exactly one writer, and it is a
seed file: `scripts/seed/05-seed-tshepo.sql:10` (`INSERT INTO tshepo.consent_directive`).
`ConsentDirectiveEntity` is referenced **only** by its own repository — there is no `save(...)`
call anywhere in `services/tshepo-service/src/`.

**Consequence, recorded plainly:** the legacy store is *read-only in production code and
populated only by seed data.* Its Step 5 check can therefore only ever return true for
seeded rows. If that monolith's `PolicyEngine` were on the live path for clinical reads, every
non-seeded patient would be denied — the same fail-closed shape Checkpoint 5's evaluate-wire
finding describes (`CP5_CONSENT_CONVERGENCE.md`). This is an argument for retirement, and also
the reason retirement must be a *repoint*, never a *deletion*.

### 4.3 Where the monolith still sits on a path

`infra/envoy/envoy-runtime.yaml` still routes to `cluster: tshepo_service` (:192, cluster
defined :298) and its own comment at :43 calls it "the legacy default-ALLOW `tshepo-service`
monolith". `docker-compose.runtime.yml` still names it. `infra/envoy/envoy.yaml` (the
non-runtime config) points ext_authz at `tshepo-authz-service`, not the monolith. So the
monolith is *deployed and routable* even though the ext_authz decision boundary has moved.

### 4.4 The criterion — all five must be true before `V003__consent.sql`'s table may be dropped

1. **`PolicyEngine` Step 5 in the monolith is repointed or dead.** Either
   `services/tshepo-service/.../core/PolicyEngine.java` `checkConsent` calls
   `tshepo-consent-service`'s `GET /v1/consent/evaluate` (the contract the rest of the estate
   uses), **or** the monolith's `AuthorizeController` is proven to serve zero production
   traffic. "Proven" = no route in `infra/envoy/envoy-runtime.yaml` reaches `tshepo_service`,
   and access logs over a full business cycle show zero requests. Not "we think it's unused".
2. **A migration audit shows zero rows of value**, run against every live database named
   `tshepo`: `SELECT count(*) FROM tshepo.consent_directive WHERE status = 'ACTIVE'` returns 0
   in every environment, **or** every such row has been migrated into
   `tshepo_consent.consent_directive` with a recorded mapping. The two schemas are not
   isomorphic — `grantee_scope`/`resource_scope`/`purpose_of_use` must be mapped onto
   `grantee_ref`/`scope`/`purpose` **and** a `fhir_consent_json` body must be synthesised. A
   row that cannot be mapped is a blocker, not a rounding error.
3. **`scripts/seed/05-seed-tshepo.sql` no longer seeds it**, and whatever that seed exists to
   make work (local/dev authorization flows) is made to work against
   `tshepo_consent.consent_directive` instead. Deleting the seed alone would silently break a
   dev path.
4. **A negative control proves the replacement fires.** With the repoint in place, an actor
   with no directive in `tshepo_consent.consent_directive` must be DENIED, and the denial must
   name the consent service as its source. A check that cannot fail is not a check
   (`docs/` law: prove every check by breaking what it guards).
5. **Consent enforcement is actually on the live path** (§6) — because retiring the legacy
   check while the replacement is disconnected converts a weak control into no control.

Until all five hold, the table, entity, repository, migration and Step 5 **stay**, and this
document is the record of why they are still there.

**Interim obligation:** `tshepo.consent_directive` must not gain new writers, new readers, or
new columns. The guard in §5 pins it in the allowlist under an explicit `LEGACY` note, so a
change to that line is a change somebody has to justify.

---

## 5. The guard

`scripts/guard/check-consent-source-of-record.sh`.

It extracts every `CREATE TABLE` from `services/*/src/main/resources/**/*.sql` whose
schema-or-table reference contains `consent`, and compares the result against an explicit
allowlist. A **new** consent store fails the guard until someone consciously adds it and states
what it is. A **stale** allowlist entry (a table that no longer exists) also fails, so the
allowlist cannot quietly rot into fiction. It additionally asserts that this document still
exists and still designates `tshepo-consent-service`.

The allowlist is the fourteen `service | table` pairs inventoried in §2. Each carries a
one-line note; the notes are the point — the guard's value is that adding a row forces someone
to write down which of the five roles in §3 the new table has.

The guard is source-only: it needs no cluster and can never "skip to green".

---

## 6. What this designation does NOT claim

| Claim | Status |
|---|---|
| `tshepo-consent-service` is the SoR for grant/revocation | **DESIGNATED** (this document) |
| The evaluate wire authz → consent is correct | **FIXED** in Checkpoint 5 (`CP5_CONSENT_CONVERGENCE.md`) |
| Consent is enforced on the live request path | **NO** — the PDP is not on the ingress path (ext_authz off). Unchanged by this document. |
| Consent for non-`Patient` clinical resources evaluates against the right subject | **NO** — `AuthzInternalRequest` carries no field naming the patient a non-Patient resource concerns (`CP5_CONSENT_CONVERGENCE.md` §"Retained gap") |
| Mvumo always materialises grants into the SoR | **UNPROVEN** — `mvumo.tshepo-consent.enabled=false` silently skips it (§2.2); Checkpoint 1 recorded materialisation as PARTIAL, "needs runtime proof" |
| The narrow domain stores reconcile with the SoR on revocation | **UNKNOWN** (§2.6) |
| Legacy `tshepo.consent_directive` is retired | **NO** — criterion recorded (§4), not met |
| Historical rows in `experience-bff.consent_preferences` have been assessed | **UNKNOWN** (§2.4) |

Designating a source of record is a governance act. It makes the *next* defect legible; it does
not by itself enforce anything.

---

# Resolved 2026-08-02 — the UNKNOWNs measured, and the findings closed

## Measured, not assumed

Queried directly against the live preview databases:

| Store | Rows |
|---|---|
| `tshepo_consent.consent_directive` (**the source of record**) | **0** |
| `tshepo_consent.consent_audit` | **0** |
| `mvumo.consent_request` | **0** |
| `mvumo.consent_request` linked to the SoR | **0** |
| `tshepo` database (the legacy store) | **does not exist** |
| `tshepo-service` deployment (the legacy monolith) | **not deployed** |

### The finding that matters most

**The consent chain is empty end to end.** Not mis-wired — empty. No consent directive has ever
been captured in this estate.

So switching consent enforcement on today would deny **every** clinical access, and it would not
be because of the wire defect (fixed in 5.1) or a policy disagreement. It would be because there
is nothing to find. Fail-closed against an empty store is indistinguishable, from the outside,
from fail-closed against a revoked directive — and the estate would look like consent enforcement
working perfectly while actually blocking everyone.

**This is a hard precondition for turning on ext_authz Stage 2**: consent capture has to produce
directives before consent evaluation can mean anything.

## Legacy store: two of five retirement conditions already met

The `tshepo` database does not exist and `tshepo-service` is not deployed. Conditions 1 (zero
production traffic) and 2 (zero rows of value) are therefore satisfied **by measurement**, not by
argument. The code remains in the repository and is not deleted; what changed is that its risk is
now quantified as zero in this environment rather than assumed.

Conditions 3–5 (reseed against the SoR, negative control, enforcement on the live path) stand.

## Mvumo's silent de-designation: closed

`TshepoConsentClient.createDirective` logged a warning and returned `null` when
`mvumo.tshepo-consent.enabled=false`. The grant was captured in Mvumo, never reached the source of
record, and **nothing downstream failed** — one configuration flag could silently de-designate the
SoR while consent capture appeared to keep working.

It now **throws**. Mvumo owns the consent *experience*; it does not own grant state. A consent
record that does not reach the source of record is not a consent record, so failing to materialise
is a failure of the capture rather than a skipped optional step.

## Authority is on the decision path

`AuthorityResolver` is no longer shelf-ware. `PolicyEngine` resolves an `AuthorityBinding` at
step 1.5 — from the introspected duty token plus the VARAPI revocation store — and records the
resulting state on **every** terminal decision, allowed or denied. An access refused for want of
authority is exactly the case an audit trail must be able to explain.

Metric tags are bounded: an authority state from a closed vocabulary
(`actionable` / `no_appointment` / `suspended` / `expired`) and the verdict. Appointment and
licence identifiers are real operational data and stay out of metrics.

## Remaining honest gap

The BFF's `consent_preferences` orphan could not be counted: the BFF's database is not among the
namespace's databases under any of the names searched, so the table's contents are still
**UNKNOWN** — a smaller and better-specified unknown than before, but not resolved.

---

# Consent PROVEN working end to end, 2026-08-02

The chain was **empty, not broken**. Exercised against the live preview services:

| Step | Result |
|---|---|
| `POST /v1/consent` with a complete FHIR R4 Consent | **201** — directive `70309319-…` created |
| Stored | `consent_directive` **1**, `consent_audit` **1**, `event_outbox` **1** |
| `GET /v1/consent/evaluate` — the granted actor | **`permitted: true`**, `consentId` matches, `allowedScopes: ["read"]` |
| `GET /v1/consent/evaluate` — an actor with no directive | **`permitted: false`, `reason: NO_ACTIVE_CONSENT`** |
| `DELETE /v1/consent/{id}` (revoke the proof) | **200** — `status: REVOKED`, audit rows **2** |

The audit chain retained both events after revocation, which is the correct behaviour: the record
of a consent decision must outlive the consent.

Validation is genuinely enforced on the way in — the first two attempts were rejected with
`fhirConsentJson is required` and then `FHIR Consent must have at least one category`. The store
does not accept a malformed directive.

**So the precondition for enabling consent enforcement is not a code fix. It is that consent has
to be captured.** Directives exist only where someone has been asked.

## Honest feedback when consent is missing

A person who has never been asked and a person who has withdrawn are not in the same situation,
and telling both *"access denied"* turns a next step into a dead end.

`PolicyEngine` now distinguishes them:

| Consent-service reason | Code returned | What the caller is told |
|---|---|---|
| `NO_ACTIVE_CONSENT`, `CONSENT_EXPIRED`, `NO_CONSENT_FOUND` | **`CONSENT_REQUIRED`** | *"…no consent covering this access has been granted yet. Ask the person to grant consent, or record an alternative lawful basis."* |
| anything else (withdrawal, explicit refusal) | `CONSENT_DENIED` | *"The person's consent does not permit this access."* |

Three properties of that split:

- **Both still refuse.** This changes what the caller is *told*, never whether access is granted.
- **It is an allowlist.** A refusal reason added to the consent service later defaults to the
  non-actionable message, so a new reason can never silently become "just ask again".
- **Withdrawal is deliberately excluded.** Someone who has withdrawn has given an answer.
  Inviting the caller to ask again would be misleading, and a nudge to pressure the person.
