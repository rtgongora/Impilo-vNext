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

So `NO_ALLOW_RULE` on a clinical read is **correct behaviour, not a defect**. Consent is unreachable
because the thing consent exists to gate — reading a patient record — is currently authorized for
nobody.

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
| Consent for non-`Patient` clinical resources | **GAP** — unchanged, see `CP5_CONSENT_CONVERGENCE.md` |

The gate condition is met in the code path. The honest boundary is the fifth and sixth rows: the
estate cannot demonstrate it end-to-end, and the reason is not that consent is misconfigured but
that **no clinical read is authorized for anyone**, so nothing ever gets far enough to have its
consent checked.

That is missing functionality, and it should be named as such rather than closed by writing a rule
to make the gate demonstrable. A green end-to-end proof obtained that way would be evidence about
the rule someone added, not about whether the estate governs clinical access.
