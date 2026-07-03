# DEC-0001 — Coverage pre-service enforcement

**Status:** PARKED — product-owner decision required
**Raised:** 2026-07-03 (journey-closure session, backlog ⑥)
**Owner:** Product owner (health financing policy)

## Question

Should the platform verify a patient's medical-aid coverage **before care is
delivered** (at queueing/encounter start), or only at billing time?

Today nothing enforces coverage anywhere pre-service; Wave 4 adds bill-time
coverage application (`POST /costa/v1/bills/{id}/apply-coverage`): eligibility
is checked when the bill is assembled, the covered/patient split is computed,
and any shortfall is collected from the patient.

## Options

### A — Hard block at the sorting desk / encounter start
Eligibility is checked when the patient is queued; ineligible or unverifiable
patients cannot proceed without an explicit override.

- **Pros:** no bad debt; payer rules enforced at the door; clean revenue cycle.
- **Cons:** care-access risk (registry outages or data gaps deny care);
  clinically unacceptable for emergencies; coverage data is immature (0 member
  records in production-like environments today) — a hard gate would block
  nearly everyone.

### B — Advisory surfacing pre-service + bill-time enforcement (recommended)
Coverage status is *shown* (banner on queue/booking/encounter surfaces:
"covered / not covered / unverifiable") but never blocks care. Enforcement —
split, claim, shortfall collection — happens at billing, as built in Wave 4.
Emergency pathways are always exempt from any future hardening.

- **Pros:** preserves access to care; staff and patients know the financial
  position early; no registry-outage denial; aligns with COSTA's existing
  deferred-emergency-charge and waiver models; graduates naturally to option A
  for electives once coverage data matures.
- **Cons:** bad-debt exposure remains for patients who cannot pay the
  shortfall after service.

### C — Bill-time only (status quo + Wave 4)
No pre-service signal at all; coverage is applied when the bill is assembled.

- **Pros:** zero additional friction and zero build cost.
- **Cons:** patients discover shortfalls after care; front-desk staff cannot
  set payment expectations; highest bad-debt exposure.

## Recommendation

**Option B.** Emergency care always exempt. Revisit option A for *elective*
services once member enrolment data is real and registry availability has a
track record. The advisory banner consumes the same eligibility endpoint Wave
4 already wires (`POST /internal/v1/eligibility/check`), so B is an
incremental UI/BFF surface, not a new integration.

## Consequences of deferral

Until decided, the estate behaves as option C with the Wave-4 billing
machinery. No pre-service surface will be built without this decision.
