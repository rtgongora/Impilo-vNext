# Scenario D — Medical Coverage (Runbook)

Coverage broadly: member enrolment, eligibility, payer/patient split, claim
lifecycle, and the failure paths. Scenario D has no separate script — its
assertions live inside Scenario B, which drives coverage end-to-end against the
live estate.

## Where each D concern is proven

| Concern | Proof |
|---|---|
| Member enrolment | Scenario B step 1 (coverage-service member + plan binding) |
| Eligibility check | costa `applyCoverage` → coverage `POST /internal/v1/coverage/eligibility/check` |
| Payer/patient split | ELIGIBLE 90/10 split asserted (insurer 30.00 / patient 7.50 on 37.50) |
| Claim filing | cv_claim created on bill finalize when insurerPayable > 0 |
| Adjudication | `POST /internal/v1/coverage/claims/{id}/adjudicate` → ADJUDICATED + outbox event |
| Failure path: no cover | un-enrolled journey → `INELIGIBLE:NO_COVER`, 100% patient payable |
| Shortfall settlement | REMAINDER intent → SANDBOX card capture → PAID |

Run it: `bash test/integration/scenario-b-billing-coverage-shortfall.sh`
(see [scenario-b-billing-coverage.md](scenario-b-billing-coverage.md)).

## Coverage data model (preview)

- coverage-service (`coverage` DB, port 8140) owns plans/members/claims:
  `cv_coverage_plans` seeds `COV-MOHCC-CORE` (90/10) and `COV-PRIVATE-PLUS`
  (80/20 + deductible/cap).
- costa mirrors plan economics in `costa_insurance_plans` (V019) and keys the
  split off `EncounterEntity.insurancePlanId` via the existing ExemptionEngine.
- Tariffs are AHFOZ-indicative placeholders (V020); the real AHFOZ schedule
  arrives via costa's governed import, not seeds.

## Not yet proven / out of scope

- Expired-membership and cap/deductible-exhaustion negative paths (engine
  supports them; not yet scripted).
- Pre-service enforcement at the sorting desk — PARKED decision
  `docs/decisions/DEC-0001-coverage-pre-service-enforcement.md`.
- Real payer integration (EDI/portal); adjudication is a scriptable endpoint.
- Subsidy/waiver interplay is covered by the separate L4 access-value lane, not
  this journey.
