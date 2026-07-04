# Journey Closure — Scenarios A–D: Final Report

Go-live readiness mission, journey-closure session (2026-07-03 → 2026-07-04).
Branch `claude/web-session-anchor-nnnkf6`, ~76 commits, estate serving `2bb73c1c`
at close. Mandate: make four PO scenarios **actually work** end-to-end on the
live k3s preview estate with repeatable automation — remediation for real, no
mocks, backlog ①–⑦ batched into the waves.

## Outcome

| Scenario | Status | Proof |
|---|---|---|
| **A** — clinician: login → context → shift → patient → queue → encounter → lab → imaging → teleconsult+media | ✅ green (24 checks) | `scripts/e2e/scenario-a-clinical-journey.sh` |
| **B** — billing → coverage split → claim → adjudication → shortfall card → settled | ✅ green (12 checks) | `test/integration/scenario-b-billing-coverage-shortfall.sh` |
| **C** — Fundo: catalog → enrol → lessons → certificate → governed CPD → notification | ✅ green (12 checks) | `scripts/e2e/fundo-learner-journey.sh` |
| **D** — coverage incl. failure paths | ✅ proven inside B (`INELIGIBLE:NO_COVER` path) | see B + [runbook](../journeys/scenario-d-coverage.md) |

Combined runner `scripts/e2e/run-all-scenarios.sh` chains all three with
transcript evidence under `reports/journeys/`; post-deploy smoke wrappers
`scripts/test/run-scenario-{a,b}-smoke.sh` gate on estate reachability. All
green together on the final estate (48 checks).

## Backlog ①–⑦ disposition

| # | Item | Disposition |
|---|---|---|
| ① | COSTA↔MusheX money loop | ✅ closed — synchronous intent handoff + persisted `mushex_payment_intent_id`; existing status loop settles PAID (`701ed7abf`) |
| ② | Work-access request seam | ✅ closed in Wave 1 (request-access UI over existing onboarding backend) |
| ③ | Pharmacy → Dura stock | ✅ closed — dispense emits `pharmacy.stock.movement.requested`; Dura consumer decrements ledger (`27fe9ef24`) |
| ④ | Teleconsult VITO guard | ✅ closed — 422/503 fail-closed intake validation (`4dae8e1c2`) |
| ⑤ | Fundo notification flip | ✅ closed — real comms-hub delivery incl. two dead-by-construction provider fixes (`5b86637aa`, `1099a8b0d`) |
| ⑥ | Coverage pre-service enforcement | 📄 PARKED by decision `docs/decisions/DEC-0001-coverage-pre-service-enforcement.md` (recommend advisory + bill-time) |
| ⑦ | VARAPI bootstrap policies + NHUME/UBOMI tests | ✅ closed — policy enforcement (`3149f3a72`, 11 tests) + 13 new test-slice tests (`28769d536`) |

## Infrastructure landed in-chart

- **Orthanc** (`templates/orthanc.yaml`, jodogne/orthanc-plugins 1.12.4 via
  mirror.gcr.io) — real DICOM backend; imaging loop proven upload→forward→report→OROS.
- **LiveKit** (`templates/livekit.yaml`, v1.8.4) — media tokens validated
  in-cluster; browser join pending firewall ports (PO action; instructions delivered).
- **Kafka listener opt-ins** — `microservice.yaml` env now overridable; opted-in:
  oros, pacs-adapter, costing-engine, mushex, coverage, inventory, pharmacy,
  learning, varapi. The estate's event loops were dormant before this.

## The recurring defect class: dead by construction

The single biggest finding. Event/REST loops that compile, deploy, and log
nothing — because no runtime path ever connected them:

- pacs↔OROS and learning↔varapi topic mismatches (producer and consumer on
  different topic names).
- No TrustContext on Kafka consumer threads (rollback-only poison + infinite
  redelivery) — fixed with synthetic-context + ack guards.
- Learning→comms-hub provider: wrong body contract AND missing v1.1 companion
  headers; nothing ever *wrote* notification intents on certificate issuance.
- Costa PCT consumer: strict `EncounterType.valueOf` threw on real PCT values.
- localhost default base-URLs in-cluster (mushex→credential-verification).
- Emit-before-save NPEs (learning `openLesson`).

**Lesson**: "service builds + endpoint 200s" proves nothing about loops. Only
journey-level steel threads on a live estate surface this class.

## Governance boundaries held (honest limits)

- Fundo never awards CPD points — varapi candidates require council/registry
  acceptance into an IN_PROGRESS cycle (the proof script drives it as a
  registry actor).
- Comms hub renders strictly from registered templates (national-pod authority).
- Claim adjudication is a scriptable endpoint, not a payer integration.
- AHFOZ tariffs are indicative placeholders (PO: real schedule via costa's
  governed import).
- Access-request approval stages accounts; assignment stays an admin action.

## Open items

1. **Browser video join** (task #17) — firewall 7880/7881 TCP + 7882/UDP to
   10.50.1.67; then two-context Playwright TrackSubscribed assert.
2. **TUSO import row-approve silent 403** (task #9) — Spring Security DEBUG
   rider deployed, needs re-probe.
3. Scenario D deep negatives (expired membership, cap exhaustion) — engine
   supports, not yet scripted.
4. Payer EDI, real card rails (SANDBOX rail proven), DICOM modality acquisition —
   out of preview scope by design.

## Runbooks

`docs/journeys/scenario-{a-clinical-journey,b-billing-coverage,c-fundo-learner,d-coverage}.md`
+ `docs/journeys/maestro-parity-note.md`.
