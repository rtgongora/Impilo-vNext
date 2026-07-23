# National e-Orders, Fulfilment & Telemonitoring — Implementation Roadmap

**Programme anchor documents** (this roadmap is the schedule; those are the truth):
[Volume II specification](../telemedicine/NATIONAL_EORDERS_FULFILMENT_TELEMONITORING_SPECIFICATION.md) ·
[backlog OF-B1..OF-B30](../telemedicine/telemedicine-implementation-backlog.md) ·
[gap matrix R41–R74 / OF-G1..21](../telemedicine/telemedicine-traceability-gap-matrix.md) ·
[journeys #41–#70](../telemedicine/telemedicine-journey-catalogue.md).

**Operating rules.** One wave at a time; an epic is DONE only when its journeys run green on the
live preview estate (the OF-B30 rig grows per wave — never authored last). Evidence tags in the
matrix are updated at each wave close. Open decisions (OD-11..17, Vol I §32) carry fail-closed
interim postures so no wave blocks on a pending decision.

---

## Milestone view

| Milestone | Meaning | Target state | Status |
|---|---|---|---|
| **M0 — Spec pack** | Two-volume canonical spec + shared matrix/backlog/journeys | PO-reviewable, gates green | ✅ DONE 2026-07-23 |
| **M1 — Trust spine live** | Signed, versioned, token-claimable prescriptions + order lifecycle on the estate | Wave OF-A deployed + journeys #50/#51 legs green live | ✅ DONE 2026-07-23 (live trust-spine proof 11/11; local rig 36/36) |
| **M2 — Marketplace live** | RFO round → offer → commitment with real stock reservation, fail-closed eligibility | Journeys #41 (to reservation), #48, #49, #52 green live | ✅ DONE 2026-07-23 (live proof 28/28 @ `8353d93e1`) |
| **M3 — Money + dispense closed** | Coverage/liability/payment wired into selection; dispense bound to claims; pickup/delivery | Wave OF-B; journeys #42–#47, #53–#57, #68–#70 | 🔶 CURRENT |
| **M4 — Telemonitoring live** | Plans, thresholds, device readings, alert ladder, CHW workflow | Wave OF-C; journeys #58–#66 | ⬜ |
| **M5 — Governance close** | Cold-chain/drone lanes honest, fairness analytics, full-suite rig | Wave OF-D; #43, #67, full #41–#70 | ⬜ |

---

## Wave OF-A — P0 foundations (✅ COMPLETE 2026-07-23)

**Built + test-green + deployed + live-proven (2026-07-23, closing SHA `8353d93e1`):**

| Epic | Delivered | Evidence |
|---|---|---|
| OF-B1 order versioning | Immutable versions + supersedes chain, §9.1 target states T1–T9, amend/hold/resume/revoke/replace/error-mark | oros 189 tests; rig J-OO-1 |
| OF-B2 prescription + signing + token | Aggregate w/ server-side repeats counter, detached JWS (tshepo-keys `PRESCRIPTION_SIGNING`, fail-closed), single-active claim token, legacy silo frozen (OD-13) | rig J-OO-1..5 (36/36) |
| OF-B3 safety seam | Coded allergy SoR (PCT V052), rules-governance over `clinical.rule_definitions`, engine bugfixes, BFF check activated | pct 211 / ckp 102 / bff 1168 |
| OF-B4/5/6 RFO + eligibility + offers | Request/invitation/offer/selection machines, structural PII-minimisation (leak-tested), fail-closed six-precondition eligibility, §8.9 commitment w/ RC-1/2/3/6/7 | msika-flow 161 |
| OF-B11 DURA wiring | Reservation events + TTL expiry sweep; `mf_reservations` demoted to projection | inventory 110 |
| OF-B29 (controlled half) | Never-OPEN, pre-filtered invitations, register write gates commitment | in msika-flow suite |

**Wave close (all four closure items done 2026-07-23):** estate rollout of the touched services; `PRESCRIPTION_SIGNING` key provisioned live; M1 trust-spine live proof **11/11**; M2 marketplace live proof **28/28** (#41 order→RFO→offer→selection→DURA reservation, #48 TTL expiry, #49 stock fail-close, #52 controlled gating) against real VARAPI/TUSO/VASHANDI; matrix/backlog evidence tags updated.

**Three live-caught bugs — all fixed @ `8353d93e1`:**
- **BUG-1 (Blocker):** TusoClient/VarapiClient sent no trust headers → tuso tenant-isolation guard 500'd every eligibility read → PREMISES_UNVERIFIED fail-closed all invitations. Fixed with the header-propagating RestClient idiom + tenant-scoped-first/reference-fallback facility reads.
- **BUG-2 (Minor):** DURA over-reserve refusal was a raw 500 → now a coded 409 (INSUFFICIENT_STOCK/RESERVATION_CONFLICT envelope); fail-close behaviour unchanged.
- **BUG-3 (Blocker-in-waiting):** `mf_reservations` V001 FKs (mf_orders/mf_order_lines) would have FK-rolled-back the first real commitment after taking DURA holds → dropped in V007 (projection references OROS ids by design).
- F1 (config): msika-flow `VARAPI_URL`/`TUSO_URL` now durable in preview helm values (were localhost-defaulted in-pod).

**Honest deferrals inside OF-A** (recorded, not hidden): commitment steps 7–8 (financial) are
SKIPPED pending OF-B9/B10; fulfilment dispatch after commitment is a PARTIAL seam (RC-8 reserved);
ranking is the basic ranked-because set pending OD-12.

---

## Wave OF-B — P1 marketplace-complete + finance (M3 — CURRENT)

Four parallel lanes over the OF-A spine; serialize only where files collide.

| Lane | Epics | Delivers |
|---|---|---|
| Comparison + finance | OF-B7, OF-B8, OF-B9, OF-B10 | Patient comparison UX w/ full ranking transparency; ZIBO medicine registry + payer formulary; per-offer liability (COSTA/Ruvimbo) into checkout; MusheX payment step + escrow-on-PoD + reconciliation |
| Dispense | OF-B12, OF-B15, OF-B16 | Claim↔dispense linkage (closes OF-G4, the last P0-severity gap), substitution + prescriber clarification loop, pickup proofs/SMS reference/locker orchestration |
| Logistics | OF-B17, OF-B18 | Nhume RFO-delivery orchestration + escrow release on PoD; custody-grade ladder + named-recipient verification |
| Diagnostics/split | OF-B13, OF-B14 | Lab/imaging RFO profiles + home collection; multi-provider split fulfilment |

**Wave gate:** journeys #42–#47, #53–#57, #68–#70 green live; #41 green end-to-end
(order → paid → dispensed → SHR write). Decisions consumed: OD-12 (ranking/broadcast),
OD-15 (controlled custody detail), OD-17 (attested stock).

## Wave OF-C — telemonitoring (M4)

Strictly ordered start: **OF-B22** (new clinical-plane `telemonitoring-service` skeleton + plan
engine) first, then **OF-B21 / OF-B24 / OF-B25** in parallel (programme model; device
registry/lifecycle + assignment gate; ingestion/normalisation + single SHR writer), then
**OF-B26** (alert + escalation ladder — needs plans and gated readings), then
**OF-B23 / OF-B27 / OF-B28** (CHW workflow; patient/caregiver surfaces; monitoring command
workspace). **Wave gate:** journeys #58–#66 green live. Decisions consumed: OD-14 (attestation/BYOD),
OD-16 (MONITORING OrderType).

## Wave OF-D — governance + maturity (M5)

OF-B19 (cold-chain IoT), OF-B20 (drone lane as governed capability — never claimed live until it is),
OF-B29 second half (anomaly + fairness analytics — now with real marketplace volume to tune against),
OF-B30 closure (full-suite #41–#70 + CI-runnable aggregator). **Wave gate:** #43, #67, full suite.

---

## Cross-cutting queue (outside this programme, held in order)

1. **Security wave** — make full-preview enforce prod auth (PO-deferred to after the telemedicine/
   e-orders programme; plan in the preview→prod auth hardening memory/docs).
2. **OD decisions for PO** — OD-11 (signature scope), OD-12, OD-13 (legacy cutover timing),
   OD-14, OD-15, OD-16, OD-17. None blocks a wave start; each upgrades an interim posture.
3. Parked: LiveKit external DNS/cert; Helm rev4 reconcile at next full deploy cycle.

**Cadence.** Each wave: build lanes → suite-green → estate deploy → rig tranche green → matrix
evidence updated → wave-close summary to PO. Same rhythm that carried TM-B1..B20.
