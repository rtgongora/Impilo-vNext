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

**Answer: the gate is reachable.** Derivation produces types in the set, so the literal list is not
mis-specified for the paths the BFF actually calls. A chained test is worth writing.

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

This is consistent with the known finding that no role-bearing rule fires for browser users. **The
blocker is policy-rule seeding, not the consent gate**, and widening `CLINICAL_RESOURCE_TYPES` would
not move it by a single decision. Left as-is, per scope.

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
| Consent for non-`Patient` clinical resources | **GAP** — unchanged, see `CP5_CONSENT_CONVERGENCE.md` |

The gate condition is met in the code path. The honest boundary is the fifth row: the estate cannot
yet demonstrate it end-to-end, because no ALLOW rule lets a clinical request reach Step 5. That is
policy-rule seeding work, and it is a separate Phase 0 item.
