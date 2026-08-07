# Phase 0 · D1 — A revoked consent demonstrably blocks a read

Gate condition #3 of 5 (`vnext-hybrid-federation-target-architecture.md:2935`).

Measured **2026-08-07** against the live preview estate. Companion to
[`CP5_CONSENT_CONVERGENCE.md`](CP5_CONSENT_CONVERGENCE.md), which fixed the wire contract this
proof depends on.

---

## 1. Reachability — measured, not read

The consent gate keys off a fixed literal set (`PolicyEngine:75`, `CLINICAL_RESOURCE_TYPES`), and on
the ext_authz path `resourceType` is not supplied by the caller — `AuthorizeController:180` derives
it from the last meaningful path segment. So the gate is only meaningful if real traffic actually
derives a value in that set.

**Database: `tshepo_authz`, schema `tshepo_authz`, table `policy_decision_log`.**

Two databases carry this table. The running PDP writes to `tshepo_authz`, established two ways that
agree:

- `tshepo-authz-service` deployment env: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/tshepo_authz`
- row recency: `tshepo_authz` holds **1,952 rows**, newest `2026-08-07 14:04`; `oogate_tshepo_authz`
  holds the table with **0 rows**.

> ⚠️ The table is in schema `tshepo_authz`, **not** `public`. An unqualified
> `select … from policy_decision_log` fails with `relation does not exist` in *both* databases — a
> query that looks like a clean negative answer while actually never having looked.

### Observed clinical resource types

Of **128 distinct** resource types observed, exactly **two** intersect `CLINICAL_RESOURCE_TYPES`:

| resource_type | rows | most recent |
|---|---|---|
| `encounters` | 15 | 2026-08-07 08:06 |
| `patients` | 4 | 2026-08-07 06:14 |

The remaining eight literals (`Patient`, `Encounter`, `Observation`, `DiagnosticReport`,
`MedicationRequest`, `observations`, `diagnostic-reports`, `medication-requests`) have **never** been
observed. A relaxed `ilike` sweep for patient/encounter/observation/medication/diagnosis substrings
returns the same two rows and nothing else — so this is not a naming near-miss.

**Answer: the gate is reachable — but only for collection paths and UUID-identified records.**
Derivation produces types in the set, so the literal list is not mis-specified for the paths the BFF
actually calls, and a chained test is worth writing. The qualification matters and is measured in
§2: `/v1/patients` and `/v1/patients/{uuid}` both derive `patients`, whereas
`/v1/patients/{non-uuid-cpid}` derives the identifier itself and never reaches the gate. All four
`patients` rows above carry a null `resource_id`, i.e. they were collection reads.

## 2. The finding that outranks the test

Reachable is not the same as reached. **All 19 clinical decisions were `DENY` with
`deny_reason = NO_ALLOW_RULE`.**

That reason is emitted at `PolicyEngine:676`, inside `evaluatePolicies`, which is called at
`PolicyEngine:350` — and line 351–353 returns immediately on a deny. Consent is **Step 5**, at
`PolicyEngine:411`. Execution never gets there.

Corroborating the whole log:

- **0** of 1,952 decisions carry any consent-related `deny_reason`.
- 1,245 of 1,338 denials are `NO_ALLOW_RULE`; 87 more are `NO_MATCHING_RULES`.

> **Consent evaluation has never once executed in the live estate.** Not because the resource-type
> gate is wrong, but because every clinical request is refused one step earlier, for want of a
> matching ALLOW rule.

### Why no rule matches — measured, because the answer is not what it looks like

`NO_ALLOW_RULE` is only returned when the rule list came back **non-empty** and nothing in it
matched (`PolicyEngine:674`); an empty list yields `NO_MATCHING_RULES` instead. So rules were found
and rejected. Three plausible causes, all eliminated:

- **Not tenancy.** All 575 rules live in the REGISTRY tenant `…0000-0000…0001`, and 18 of the 19
  clinical decisions came from the CARE tenant `…4000-8000…0001`, which owns zero rules. But
  `PolicyCacheService.mergeGovernanceRules` merges the governance tenant's rules into every tenant
  precisely for this reason, so the CARE tenant sees all 575.
- **Not a matching defect.** 590 CARE-tenant `TREATMENT` requests matched ALLOW rules successfully
  on experience-plane resources (`visibility`, `appointments`, `notifications`, `affiliations`).
  The same actor types (`OPERATOR`, `CITIZEN`) are allowed elsewhere.
- **Not absent rows.** The rules exist. What is absent is their subject matter.

**Of 575 rules, exactly two name a clinical resource type:**

| resource_type | effect | role | actor_type | action |
|---|---|---|---|---|
| `observations` | ALLOW | CLINICIAN | PROVIDER | POST |
| `observations` | ALLOW | NURSE | PROVIDER | POST |

Both are writes. **No rule permits READING `patients` or `encounters` — for any role, any actor
type, any action.** The 575 rules cover experience, admin, asset, and regulatory surfaces; clinical
read authorization has never been written.

So `NO_ALLOW_RULE` on a clinical read is **correct behaviour, not a defect** for a role-bearing
caller: no rule grants that read, so every such caller is refused.

### ⚠️ But "authorized for nobody" is false — two bypasses reach the read without a rule

Both were proved against the running PDP (`tshepo-authz-service`, port 8081), not inferred.

**1. `X-Purpose-Of-Use: SYSTEM` skips both the rule check and consent.** Step 4 returns
`continueWith(null)` for SYSTEM in *both* its no-rules and no-matching-rule branches
(`PolicyEngine:622`, `PolicyEngine:673`), and `requiresConsent` returns false for SYSTEM
(`PolicyEngine:1781`). There are exactly three references to `PurposeOfUse.SYSTEM` in all of
`src/main` — those three. **No guard restricts who may declare it**, and `purposeOfUse` is read
straight from the request header at `AuthorizeController:103`. The estate's own Envoy config
records that `x-purpose-of-use` "remain[s] client-supplied — a retained gap in
checkpoint-4/HEADER_CONTAINMENT.md".

Identical requests, one header changed:

| `x-purpose-of-use` | HTTP | verdict |
|---|---|---|
| `TREATMENT` | 403 | DENY `NO_ALLOW_RULE` |
| `SYSTEM` | 200 | **ALLOW** — `piiAccess: FULL`, `clinicalAccess: FULL`, `exportPolicy: FULL_AUDITED`, `drillDownAllowed: true` |

Both rows are in `policy_decision_log` under `actor_id='probe-d1'`. The actor id was invented and no
bearer token was sent; the probe went directly to the PDP, so it proves the **decision logic**, not
that Envoy would admit an unauthenticated caller. What it does establish is that the verdict is
role-independent and consent-independent — any request that reaches the PDP with this header gets it.

**2. A per-record read derives the identifier as its own resource type, so consent never applies.**
`deriveResourceType` walks segments from the right, skipping only blanks, `v1`, `api`, and
**36-character UUIDs**:

| path | derived `resource_type` | `resource_id` | consent evaluated? |
|---|---|---|---|
| `/v1/patients/0f8fad5b-…-70867728950e` | `patients` | the UUID | **yes** |
| `/v1/patients/cpid-12345` | `cpid-12345` | *null* | **no** |

A non-UUID identifier is indistinguishable from a collection segment to this function, so it becomes
the resource type — which is not in `CLINICAL_RESOURCE_TYPES`, so `requiresConsent` returns false.
Every `patient_ref` currently in `tshepo_consent.consent_directive` is `cpid-…` form, i.e. non-UUID.

This one composes badly with the rule gap: **writing the missing clinical read rules would not make
consent engage on per-record reads** — it would authorize them while consent stays skipped. The two
findings must be fixed together or the second becomes a silent hole behind the first.

Neither is in scope here. Both are recorded rather than touched.

### How far the SYSTEM bypass actually reaches — the JWT gate, measured

The probe above went straight to the PDP, so it proved the verdict, not reachability. Measured
against the running gateway (`envoy-55b8d45db-wgcrg`, config via `/config_dump`):

| Layer | Finding |
|---|---|
| **Envoy JWT validation** | **NONE.** The listener's HTTP filter chain is exactly `ext_authz` → `router`. No `jwt_authn` filter is configured. |
| **ext_authz failure mode** | `failure_mode_allow` absent ⇒ **fails closed** if the PDP is unreachable. Correct. |
| **Routing** | One catch-all route, `prefix: /` → cluster `experience_bff`. All traffic. |
| **experience-bff authN** | Spring Security OAuth2 resource server ⇒ **401 `www-authenticate: Bearer`** with no token. |
| **experience-bff authZ** | **It does authorize** — 97 `hasAnyRole` gates across ~149 `requestMatchers` in `SecurityConfig`. But the default is `.anyRequest().authenticated()`, and **`/internal/v1/patients` has no matcher**, so patient reads get authentication only. |

> ⚠️ **Instrument note.** The Envoy pod has no `curl` or `wget`. Querying its admin endpoint from
> inside it returns nothing, which greps as "0 occurrences of `jwt_authn`" *and* "0 occurrences of
> `ext_authz`" — a false negative that reads exactly like a finding. Query the admin port from a
> pod that has an HTTP client, and positive-control on `/server_info` first.
>
> Relatedly: the three `jwt_authn` strings that *do* appear in the dump are in the bootstrap's
> catalogue of compiled-in extensions, not in any filter chain. Envoy supports the filter; it is
> not using it. Grep-counting the dump would have called that a JWT gate.

**End-to-end through Envoy, no bearer token:**

| purpose | HTTP | where it stopped |
|---|---|---|
| `TREATMENT` | 403 | PDP — `NO_ALLOW_RULE` |
| `SYSTEM` | **401** | reached the BFF (`x-envoy-upstream-service-time: 8`); refused for want of a token |

**So the SYSTEM bypass is not an anonymous read path.** The PDP allows it — the ALLOW is in
`policy_decision_log` under `actor_id='probe-jwt-gate'` — and the BFF's own token requirement is what
stops it. That is a real control, and it is the one doing the work here, not the trust plane.

**What that leaves — scoped by what the BFF actually gates.** An earlier revision of this section
said the BFF "authenticates but does not authorize", on the strength of `@PreAuthorize` returning
zero. That was wrong, and it is the same mistake as §2's first draft: grepping one enforcement
idiom and reading its absence as absence of the capability. The BFF authorizes in a different
idiom — `SecurityConfig` carries **97 `hasAnyRole` gates** across ~149 `requestMatchers`.

What survives the correction is narrower and better evidenced:

- The chain's default is **`.anyRequest().authenticated()`** (`SecurityConfig:606`) — a route with
  no explicit matcher gets authentication and no role check.
- `PatientController` is mapped at **`/internal/v1/patients`**, and **no `requestMatchers` entry
  covers it**. The four matchers mentioning "patient" are finance patient-accounts, pharmacy
  dispense-orders, and two patient-*shares* routes. So patient record reads fall through.
- The role gates skew to writes: 50 `HttpMethod.POST` matchers against 20 `HttpMethod.GET`.
- `RoleGuardInterceptor` — despite its name and a javadoc promising it "catches unauthorized access
  as a safety net" — **`preHandle` always returns `true`** and only debug-logs. It enforces nothing.
  A guard in name only ([[name-matching-checks-lie]]).
- `IMPILO_SECURITY_ALLOW_ANONYMOUS=false` in the deployed BFF, so `SecurityConfig`'s `permitAll`
  branch is dead in this estate. Confirmed, not assumed.

So for the reads consent exists to govern, the PDP's verdict *is* the only role-aware authorization
— and that is the verdict `X-Purpose-Of-Use: SYSTEM` flips to ALLOW with `piiAccess: FULL`. The
exposure is an **authenticated privilege escalation on the fall-through routes**, not on all 409
controllers.

**Not proven:** the end-to-end read with a valid token. That needs a test credential this session did
not have. Everything up to it is measured; the last step is inference from the routing and matcher
analysis above, and should be confirmed before the finding is closed either way.

### Provenance of the running PDP — settled by content, not by stamp

Needed before any redeploy, and it does not come from where you would expect.

`/actuator/info` on the running pod reports commit **`1d12eef`** (branch
`claude/staging-ux-orchestration-remediation-Yypyl`). **That is not what the image was built from.**
The jar's own `git.properties` carries `git.build.time=2026-08-06T02:49`, while the image was created
`2026-08-07T10:03`: the `git-commit-id-maven-plugin` (`services/pom.xml:546`) skips regeneration when
the file already exists — a build in this session logged `Properties file […] is up-to-date` — so a
stale stamp survives in a reused `target/`.

Four lines of evidence, three of them content-based, all agreeing the image is **`020dc3853`**:

| Evidence | Result |
|---|---|
| Registry tag on the running digest `sha256:0691c17…` | **`preview-020dc3853`** |
| `KeycloakAdapter.class` constant pool | contains `"JWKS endpoint verified reachable"`, `"jwk-set-uri"` ⇒ has `020dc3853` |
| `BreakGlassGrantValidationService.class` | **absent** — and that file exists at `HEAD` but not at `020dc3853`, so the absence is chronological |
| Startup log | shows the post-fix JWKS banner, which `1d12eef`'s source does not contain |
| ~~`/actuator/info` / `git.properties`~~ | **`1d12eef` — wrong** |

Ancestry against the TRUE commit, which is what governs a redeploy:

- `020dc3853` **is** an ancestor of `origin/main` and of this branch ⇒ deploying strictly advances.
- Commits touching `services/tshepo-authz-service/` in `020dc3853` but not in `HEAD`: **0** ⇒ no
  revert risk for this service.
- Commits touching it in `HEAD` but not deployed: **5**.

So the running PDP *does* validate Keycloak bearers — an earlier draft of this document inferred
from the stale stamp that it could not, and that was wrong. Both the inference and the ancestry
assessment built on it were void. Establish provenance from the tag and the bytecode.

> ⚠️ **The remedy is not to add a rule so that consent becomes reachable.** Doing that would
> manufacture the gate's own precondition: the estate would demonstrate "a revoked consent blocks a
> read" only because a rule had been written to make the read reachable in the first place, with no
> clinical authorization model behind it. Deciding who may read a patient record — under which role,
> duty, subject relationship and purpose — is the 10-dimension access design, and it is substantive
> work, not a data load. Widening `CLINICAL_RESOURCE_TYPES` would not move a single decision either.
> Both left alone, per scope.

It also updates the last row of `CP5_CONSENT_CONVERGENCE.md`, which reads "the PDP is not on the
ingress path (ext_authz off)". ext_authz is now live and the PDP is deciding — the disconnect has
moved from *the PDP isn't consulted* to *the PDP refuses before it reaches consent*.

## 3. The chained proof

Both halves are real on their own side; they meet at the wire contract pinned by
`ConsentClientContractTest`. They are separate classes because the modules are separate — joining
them in one JVM would add a module dependency production does not have.

### `ConsentRevocationBlocksReadTest` (tshepo-consent-service) — 4 tests

Real H2 database, real repository, real JPQL, real `ConsentCrudService.revoke`.

This had to use a real database. `revoke()` carries no evaluation logic — it sets
`status='REVOKED'`, and the *only* thing that turns that into a refusal is the
`AND c.status = 'ACTIVE'` predicate inside `findActiveForEvaluation`. That predicate is JPQL, so a
mocked repository cannot execute it: with a mock, the test author decides what revocation means and
then asserts their own decision. That is precisely why the pre-existing coverage
(`ConsentEvaluationServiceTest`, `ConsentCrudServiceTest`) could mention `REVOKED` 12 times without
proving the gate.

- `grantThenRevoke_flipsPermitToDeny` — grant → PERMIT (naming the directive) → `revoke()` →
  DENY `NO_ACTIVE_CONSENT`; row still present, soft-deleted, with reason and timestamp.
- `revocationEvictsThePreviouslyCachedPermit` — a cached PERMIT that survived revocation would
  serve access for the whole TTL.
- `withoutRevocation_theReadKeepsBeingPermitted` — negative control.
- `revocationIsScopedToTheDirectiveRevoked` — revoking TREATMENT leaves PAYMENT standing.

### `ConsentRevocationDeniesAtPolicyEngineTest` (tshepo-authz-service) — 5 tests

Real `PolicyEngine`, real `ConsentClient`, real `RestTemplate`, real HTTP. Nothing stubs
`ConsentDecision` — the engine receives what the client parses from wire bytes, so envelope
unwrapping, reason-code mapping and the fail-closed catch-all all execute.

- `revokedConsent_flipsTheDecisionToDeny` — ALLOW while consent stands; DENY `CONSENT_REQUIRED`
  once revoked, with nothing else about the request changed.
- `consentServiceUnreachable_denies` — **fail-closed**, against a genuinely dead port so a real
  IOException takes the real catch-all.
- `consentServiceErroring_denies` — 500 ⇒ DENY.
- `consentServiceReturningErrorEnvelope_denies` — a 200 carrying no decision ⇒ DENY.
- `nonClinicalResource_doesNotConsultConsent` — negative control; asserts the *absence* of the call.

Both classes seed an ALLOW rule deliberately. Per §2 that is exactly what the live estate lacks —
without it these tests would pass for the wrong reason, showing a DENY consent had no part in.

## 4. Red-prove

Per the law that a check which passes with its guard removed is not a check, each decisive seam was
broken and the suite confirmed RED, then restored.

| Break | Result |
|---|---|
| `revoke()` no longer sets `status='REVOKED'` | **3 of 4** RED, incl. THE GATE (negative control correctly stayed green) |
| `PolicyEngine` ignores the consent verdict | **4 of 5** RED, incl. THE GATE and all fail-closed |
| `ConsentClient` fails **open** on exception | **2 of 5** RED — both fail-closed tests |

The third break leaves the error-envelope test green, correctly: that path returns
`CONSENT_RESPONSE_MALFORMED` before the catch-all is reached.

## 5. Totals

| Module | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|
| tshepo-consent-service | 72 | 0 | 0 | 2 |
| tshepo-authz-service | 414 | 0 | 0 | 2 |

## 6. Status

| Facet | State |
|---|---|
| Revocation blocks a read (consent SoR, real DB) | **PROVEN**, red-proved |
| Revoked consent ⇒ DENY at the PDP | **PROVEN**, red-proved |
| Consent service unreachable ⇒ DENY | **PROVEN**, red-proved |
| Consent gate reachable by derived resource type | **YES** — `patients`, `encounters` |
| Consent ever evaluated in the live estate | **NO** — 0 of 1,952; all clinical requests die at `NO_ALLOW_RULE` |
| Clinical **read** authorization exists at all | **NO** — 0 of 575 rules permit reading `patients`/`encounters` |
| `X-Purpose-Of-Use: SYSTEM` bypasses rule + consent | 🔴 **PROVEN LIVE** at the PDP — 403→200 on one header, FULL PII/clinical |
| …reachable anonymously? | **NO** — the BFF's own token gate returns 401. Escalation needs a valid token, any role |
| Envoy validates JWTs | **NO** — filter chain is `ext_authz` → `router` only; no `jwt_authn` configured |
| BFF authorizes independently of the PDP | **PARTLY** — 97 `hasAnyRole` gates, but `/internal/v1/patients` has none and falls through to `.authenticated()` |
| Per-record read (non-UUID id) reaches consent | 🔴 **NO** — the id becomes the resource type; consent skipped |
| Consent for non-`Patient` clinical resources | **GAP** — unchanged, see `CP5_CONSENT_CONVERGENCE.md` |

The gate condition is met in the code path, and the chained test proves it. The honest boundary is
everything below row four.

An earlier revision of this document said clinical reads were "authorized for nobody". That was
wrong, and wrong in the direction that understates risk: no *role-bearing* caller is authorized, but
the SYSTEM purpose walks past both the rule check and consent, and per-record reads skip consent on
identifier format alone. The estate does not fail closed on clinical reads — it fails closed only on
the paths that declare an honest purpose and carry a UUID.

Neither hole should be closed by making the D1 gate demonstrable. Writing the missing clinical read
rules while the derivation bug stands would authorize per-record reads *and* leave consent skipped
on them — strictly worse than today's blanket refusal.
