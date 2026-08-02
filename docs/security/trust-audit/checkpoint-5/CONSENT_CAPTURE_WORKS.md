# Real consent capture works — Checkpoint 5

**Proven live 2026-08-02** in `impilo-full-preview`, through the product path (Mvumo), not by
writing to the store.

## Final state

| Case | Result |
|---|---|
| Consent captured via Mvumo, evaluated for the right actor | **`permitted: true`** |
| Subject never asked | `permitted: false` — `NO_ACTIVE_CONSENT` |
| Right subject, wrong purpose | `permitted: false` — `NO_MATCHING_CONSENT` |
| Revoked directive | `permitted: false` — `NO_ACTIVE_CONSENT` |
| Evaluation audit written for a permit | yes — 6 rows |

The three refusals are **distinct reasons**, not one flat denial. That is what makes the
difference between "nobody has asked you yet" and "you said no" expressible at all.

## Four real defects, each blocking real capture

### 1. Mvumo could not reach the source of record

`TSHEPO_CONSENT_BASE_URL` was unset, so Mvumo used its default `http://localhost:8182` — itself.
Confirmed live: `localhost:8182` UNREACHABLE from the Mvumo pod, `tshepo-consent-service:8182`
reachable. The same localhost-fallback class as the BFF's 80 misconfigured downstreams.

### 2. Every captured consent was unmatchable

`MvumoService.buildTshepoCreateMap` hardcoded `granteeRef` to the **empty string**. A directive
with no named grantee is a general grant, and the source of record expresses that as `NULL`:

```java
"(c.granteeRef = :granteeRef OR c.granteeRef IS NULL)"
```

`''` satisfies neither arm. So consent materialised **correctly** — `tshepo_consent_id`
populated, directive `ACTIVE` — and was then **invisible to evaluation**. Proven: a granted
request produced an ACTIVE directive that evaluated to `NO_MATCHING_CONSENT` for the very
clinician it was granted to.

The same method hardcoded `purpose` to `TREATMENT`, so a request raised for any other consent
type was recorded as a treatment consent — a permission that was never given.

### 3. Evaluation could deny perfectly and had never once permitted

`ConsentEvaluationService.evaluate` was `@Transactional(readOnly = true)` but writes an
evaluation audit row: *"cannot execute INSERT in a read-only transaction"*.

The failure mode is the dangerous one. `persistEvaluationAudit` only writes when the decision
carries a `consentId` — **and only a PERMIT has one**. Every denial skipped the write and
returned cleanly; every permit threw and surfaced as **HTTP 500**.

And because a 500 from the consent service makes the PDP fail closed, the estate would have
presented as **strict consent enforcement** while actually being a broken audit write. Every
negative control anyone ran would have passed.

### 4. The PDP asked a question no grant could answer

`ConsentClient` sent `scope=read`; Mvumo grants `scope=clinical-data`. Evaluation returned
`NO_VALID_CONSENT` for a subject who had genuinely consented — which reads as a refusal by the
person rather than two services using different vocabularies. The PDP now asks for the scope
that is actually granted.

## What this changes

Consent is no longer a documented intention. A person can be asked through Mvumo, their answer
reaches the source of record, and the PDP honours it — with the audit trail written.

**The remaining precondition for enforcement is operational, not technical**: directives exist
only where someone has been asked, and this estate has been asked twice, both by this proof.

## Note on the proof data

Two synthetic directives exist: `cpid-consent-proof` (revoked as part of the proof) and
`cpid-capture-v2` (active, captured through Mvumo). Neither corresponds to a real person. They
are left in place as the evidence for this document; the audit chain retains both, which is
correct — the record of a consent decision must outlive the consent.

---

## Verification that each fix is SOURCE, CHART and RUNNING

A fix in a commit is not a fix in the estate. Re-checked all four against
`audit-deployed-provenance.py`:

| Defect | Source | Chart | Running |
|---|---|---|---|
| 1. Mvumo could not reach the SoR | ✅ | ✅ **was live-only** | ✅ |
| 2. `granteeRef` `""` → `null`, purpose from consentType | ✅ | n/a | ✅ |
| 3. read-only transaction on an audit write | ✅ | n/a | ✅ |
| 4. PDP asked `scope=read` | ✅ | n/a | ✅ **was unshipped** |

Two were incomplete when first reported:

- **(1) existed only as a live `kubectl set env`.** The chart's sole
  `TSHEPO_CONSENT_BASE_URL` was under `experienceBff/env` — the *BFF's*, not Mvumo's. The next
  render would have dropped it and Mvumo would have fallen back to `localhost:8182` (itself),
  breaking capture again. Now in `values-full-preview.yaml` and verified by rendering the chart.
- **(4) was committed but never deployed.** `tshepo-authz-service` was running `ccf515736`,
  **five CP5 commits behind** — missing the scope fix, the lawful-basis evaluator, the authority
  resolver, the consent-feedback split and the CP5 findings work. The entire checkpoint's authz
  code was in git and not in the estate.

All four consent-path services are now `IN_BRANCH`: `mvumo-service`, `tshepo-consent-service`,
`tshepo-authz-service`, `experience-bff`.

### Proof the new code executes, not merely deploys

Driving real decisions through the live PDP:

```
tshepo_authz_authority_total{state="no_appointment",verdict="DENY"} 4
```

`AuthorityResolver` runs on every terminal decision and correctly reports *no appointment* for a
probe carrying no duty token — the invariant "a context is not authority" observable in
production telemetry.

`tshepo_authz_lawful_basis_total` is absent, and that is placement rather than a blind spot: the
evaluator sits at step 5, and these probes deny at step 4 (`NO_MATCHING_RULES`) before any
lawful-basis question arises. A request denied on RBAC never asked which ground made it lawful.
It will register once traffic matches a policy rule.
