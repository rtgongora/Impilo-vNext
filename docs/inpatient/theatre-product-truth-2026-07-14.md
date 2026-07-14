# Operating-Theatre Pipeline — Reconciled Product Truth (2026-07-14)

Discovery deliverable for the theatre product-truth-recovery program (spec §1). Established by
three independent explorations (theatre domain, integration seams, async/test/seed/reporting),
reconciled and spot-verified against source. This is the baseline the program builds on; it is
NOT code-inspection-only claims — each row cites evidence.

## Headline

The perioperative **clinical episode** is genuinely **built, wired and integrated** — not a stub.
It lives in **`inpatient-service`** as a per-service state machine on ONE aggregate,
`inpatient.procedure_episode`, exposed through **two API faces over the same record** (no
duplicate-record problem — both inject `ProcedureEpisodeRepository`):

- `ProcedureEpisodeController` (`/internal/v1/procedures/**`) — clinical wizard (preop, WHO
  checklist, intraop events, PACU, postop, complete, consent).
- `TheatreController` (`/internal/v1/theatre/**`) — theatre board (OROS intake, triage, readiness,
  book, start, note draft/sign, PACU disposition, safety events, death).

FSM: `BOOKED → PREOP → READY_FOR_THEATRE → IN_PROGRESS → PACU → COMPLETED/RECOVERED`
(+ `CANCELLED`, `DECEASED`). No dedicated theatre/surgery microservice exists — by explicit
architectural decision (`docs/inpatient/ws6-discovery-and-ownership.md`,
`docs/architecture/clinical-procedure-or-context-map.md`).

## What is REAL + WIRED (evidence: `services/inpatient-service`)

- WHO Surgical Safety Checklist (SIGN_IN/TIME_OUT/SIGN_OUT, seeded per episode; start gated on
  SIGN_IN complete, emergency override w/ reason) — `V010`, `ProcedureEpisodeService`.
- MVUMO + TSHEPO consent lifecycle; **start hard-gated on consent = GRANTED** — `V011`,
  `ProcedureEpisodeService.startProcedure`.
- Multi-owner readiness gate that **fails safe with blockers** (ROOM=tuso, TEAM=varapi scope +
  vashandi roster, EQUIPMENT=asset-registry, ANAESTHESIA=preop-cleared, BLOOD=oros→madi read) —
  `TheatreService.evaluateReadiness`, `TheatreReadinessClient`; `confirmBooking` 409 unless
  emergency override + reason.
- Signable operative note → **Butano FHIR Procedure + DocumentReference** (Varapi SURGERY scope
  check) — `V018` `procedure_note`, `ButanoProcedureClient`.
- Anaesthesia scoring (ASA/Mallampati/Cormack/Aldrete + flexible registry) — `V012`,
  `AnaesthesiaScoringEngine`.
- PACU disposition incl. **death-in-theatre → PCT DeathWorkflow** — `procedure_postop_record`,
  `TheatreDeathClient`.
- Consumable consumption → inventory/DURA ledger — `V011` `procedure_consumable`,
  `InventoryConsumptionClient`. Safety-event owner routing — `procedure_safety_event`.
- Surfaces: web `work/clinical/theatre` + `[id]` + EHR wizard `ehr/[patientId]/procedures`;
  mobile `provider-app/.../TheatreProcedureScreen`; BFF `ProcedureWorkflowController` +
  `TheatreController`; authz tshepo `V029__theatre_perioperative_policy_rules.sql`; booking-service
  auto-creates the episode on a `BookingType.THEATRE` booking (`BookingIntegrationService`).

Verified now runnable: `TheatreDepthTest` (was `TheatreDepthIT`, never executed — surefire excludes
`*IT`, repo has no maven-failsafe) — 3/3 green, proving the no-fake guarantee.

## Peer services — REAL capability (mostly not yet theatre-driven)

MADI full blood chain (order/crossmatch/reserve/issue/dispatch/transfuse w/ bedside two-step
verify/react/return/reconcile); NHUME cargo transport incl. SPECIMEN/BLOOD_PRODUCT/EQUIPMENT +
chain-of-custody (**no `PATIENT` DeliveryType — gap**); OROS tissue specimens
(collection→dispatch→receipt→rejection, chain-of-custody, critical-result escalation + ack);
BUTANO Procedure/Encounter/DocumentReference FHIR; TSHEPO break-glass + consent + purpose-of-use +
policy; VARAPI facility-scoped privileges + specialties + supervision; KHULUMA on-call presence +
specialty routing + tiered SLA escalation. Telemedicine spine (PCT + BFF `TeleconsultController` +
rtc-gateway + session modes) and Fundo e-learning (`FundoWorkforceReadinessController` Vashandi-
wired, certificate/assignment/VARAPI-completion) both exist for the T&L integration.

## Truth table (spec's 11 functions)

| # | Function | Status | Evidence / gap |
|---|----------|--------|----------------|
| 1 | Surgical referral | MISSING | referral-service has no surgical type; entry is OROS order / THEATRE booking |
| 2 | Waiting-list mgmt | MISSING | only `TheatreService.triageQueue()` active-case board |
| 3 | Theatre scheduling | INCOMPLETE | per-case `scheduled_at`/`theatre_room_id`; no OR-session/list scheduler, no conflict detection |
| 4 | Theatre lists / booking | REAL+WIRED | intake→readiness→confirmBooking (409+override), board UI |
| 5 | Preop / ASA / fitness | REAL+WIRED | `procedure_preop_assessment` + scoring, auto-advance |
| 6 | Anaesthesia (+PACU scoring) | REAL+WIRED (charting shallow) | scores + intraop vitals; no agent-by-agent time-series chart |
| 7 | Operative documentation | REAL+WIRED | signable `procedure_note` → Butano FHIR, scope-checked |
| 8 | Recovery / PACU | REAL+WIRED | `procedure_postop_record`, Aldrete, disposition incl. DEATH→PCT |
| 9 | Postop inpatient | REAL via inpatient suite | disposition WARD/ICU → inpatient census; continuity is a disposition string + shared admission ref |
| 10 | Surgical discharge | REAL (generic) | inpatient discharge summary; not surgery-specialised |
| 11 | Theatre utilisation/reporting | MISSING | events emitted, **zero consumers**; no report catalog, no UI |

## Genuine gaps (drive the 7-wave program)

1. **Clinical-safety wiring** (peer capability real, not theatre-driven): theatre→MADI blood;
   NHUME patient/specimen transport (add `PATIENT` DeliveryType); OROS specimen-from-theatre loop;
   discrete swab/instrument/needle **count reconciliation** (only a note boolean today);
   anaesthesia **time-series** chart.
2. **Elective completeness**: surgical referral; managed waiting-list; OR-session scheduler +
   conflict detection; first-class TUSO theatre/OR/PACU/ICU/ward/bed/room-capacity/equipment
   entities; VASHANDI case-team; consent depth (separate procedure/anaesthesia/transfusion);
   theatre-day readiness board.
3. **Depth**: DURA implant UDI/serial + sterile instrument sets/CSSD + controlled-drug witness
   register; ZIBO seeded surgical procedure code system; COSTA surgical-case bundle; theatre
   utilisation reporting + UI.
4. **Proof/infra**: theatre runtime-proof rig; theatre Playwright spec; surgeon/anaesthetist/
   scrub/PACU-nurse/porter/coordinator **persona**; theatre-room **seed** (fresh estate hard-blocks
   readiness on `NO_ROOM`); downstream consumers for `theatre.*` events.

## Program doctrine

ONE case source of truth (`procedure_episode`), extended in place — no new theatre service, no
duplicate clinical records. New rows = link/projection tables keyed by ids (orchestration-by-
reference); peers stay SoR. New integration clients mirror `OrosOrderClient`/`TheatreReadinessClient`
(best-effort, never fabricate). Every write UNIQUE + idempotency-keyed. Telemedicine + e-learning
integrate by reference, fail-safe (never block genuine emergency care). Each wave gates on a
runtime-proof rig AND a Playwright spec being green; the Authorised Fullboot Preview Deploy waits
for the full §22 completion-gate checklist.
