# Queue Management Journey — Booking → Appointment → Check-in → Queue → Care Start → Updates

**Status**: delivered by workstream `fable/e2e-queue-booking-appointment-coordination` (2026-07-04).
This document is the honest record of what works, how the states compose, and what remains open.

## Ownership

| Concern | Owner |
|---|---|
| Appointment/booking truth | `booking-service` (`booking.appointment`, `AppointmentStatus`) |
| Slot holds / capacity rules | `scheduling-service` (slots only — no appointment truth) |
| Queue truth (definitions + items) | `pct-service` (`pct_queues` materialised from TUSO, `pct_queue_items`) |
| Journey truth | `pct-service` (`pct_journeys`, `JourneyStateMachine`) |
| Check-in orchestration | `experience-bff` `AppointmentCheckInService` (booking-first) |
| Patient notifications | `notification-service` (`/internal/v1/notify`, Mvumo-gated) |
| On-platform guidance (Nompilo) | `guidance-service` (no queue items seeded yet — gap) |

## State model

### Appointment (`booking-service` `AppointmentStatus`)
`REQUESTED → SCHEDULED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED`
with `NO_SHOW`, `CANCELLED`, `RESCHEDULED` branches. Check-in linkage truth:
`checkInStatus ∈ {NOT_CHECKED_IN, CHECKED_IN, CHECKED_IN_NO_QUEUE}` —
`CHECKED_IN_NO_QUEUE` is the explicit no-silent-gap state when a queue link
could not be established.

### Queue item (`pct-service` `QueueItemStatus`)
`WAITING → CALLED → IN_SERVICE → COMPLETED`
plus `IN_TRIAGE` (occupied by triage, caller recorded, journey untouched),
`PAUSED` (resume → `IN_SERVICE`), `NO_SHOW`, `LEFT`, `TRANSFERRED`.
Escalation is **not** a status: it is urgency (priority → `max(cur+2, 5)`) +
`escalated_at/by/reason` columns, so the item stays eligible for call-next.

The conceptual states BOOKED/CONFIRMED/CANCELLED/RESCHEDULED live on the
**appointment**, not the queue item; ARRIVED/TRIAGED and terminal dispositions
live on the **journey** (`JourneyState`). One state model per aggregate — the
three compose rather than duplicate.

### Journey (`pct-service` `JourneyState`)
`ARRIVED → TRIAGED → QUEUED → IN_SERVICE → …` (unchanged; see
`JourneyStateMachine` javadoc). Journeys now carry `appointment_id` provenance
(V031) distinguishing scheduled check-ins from walk-ins.

## The wired journey (working now)

1. **Booking/appointment**: staff (`AppointmentController`), citizen self-booking,
   booking→appointment conversion (`BookingService.confirm/convert`). Appointment
   comms templates (V009/V010) cover requested/confirmed/cancelled/rescheduled via
   BFF `AppointmentCommsWorkflowService` + `AppointmentReminderScheduler`.
2. **Check-in** (`POST /internal/v1/appointments/{id}/check-in`): BFF resolves the
   facility (appointment UUID → else caller's `X-Facility-ID`), picks the queue
   (APPOINTMENT-type → walk-in-type → first), booking starts the PCT journey
   (with `appointmentId`, facility from trust header) and enqueues at priority 3.
   Result meta carries `queue_linked` truth. *This chain was dead before this
   workstream — see Repairs below.*
3. **Walk-in**: BFF `POST /internal/v1/queue/entries` (journey start + enqueue,
   symbolic priority EMERGENCY/URGENT/LOW on the PCT 1–5 scale).
4. **Triage**: `/entries/{id}/triage` → queue item `IN_TRIAGE`; clinical triage via
   `TriageController` (acuity 1–5); `RoutingEngine` maps acuity → priority (6 − acuity).
5. **Provider actions**: call / call-next, complete, no-show, transfer, abandon,
   pause, resume, **escalate** (new; reason mandatory, optional target queue).
6. **Care start**: `POST /journeys/{id}/encounter/start` from the queue (journey
   `QUEUED → IN_SERVICE`); deliberately NOT triggered at check-in.
7. **Updates**: every queue transition writes an outbox event to
   `pct.queue.item.updated` (with `eventType`, `tenantId`, `patientCpid`);
   `notification-service` maps them to neutral IN_APP inbox messages
   (`QUEUE_CITIZEN_*` templates, V011), gated by Mvumo communication
   preferences. Escalation reasons never reach patient messages.

## Repairs delivered (previously silently broken)

- BFF `IN_TRIAGE` transition 500ed (enum missing) — fixed + regression tests.
- Queue transfer violated the `patient_cpid NOT NULL` constraint (CPID never copied).
- `updateItemStatus` changed state with **no** outbox event (silent transitions).
- BFF symbolic priorities (100/50/−10) let walk-ins outrank every triage score.
- Appointment check-in **never enqueued anyone**: booking read `journey.get("id")`
  but PCT serialises `journeyId`; booking sent a TUSO numeric facility id where
  PCT needs the facility UUID; the encounter-start-at-check-in block was dead
  code at the wrong lifecycle stage. All three layers repaired.

## Known gaps (honest, not scheduled in this workstream)

1. **Virtual queue engine — MISSING.** Only routing metadata exists
   (`pct_referrals.routing_kind/routing_pool_id`, V020). There is no
   virtual-queue entity, no virtual waiting-room admission gate, no
   provider-availability-driven routing. Telemedicine session flow is leased to
   the W0 anchor session; the queue-side handoff contract we need from W0 is:
   *teleconsult request → queue-visible waiting item → session start signal*.
   Do not treat routing metadata as a virtual queue.
2. **Automatic routing** is `EXPLICIT_V1`: callers pass `targetQueueId`; no
   workspace/cadre/load-based target selection.
3. **Shift truth is split three ways** (PCT `WorkspaceSessionService`, BFF→TUSO
   `ShiftController`, Vashandi rosters) — serialized RED item R2 on the delivery
   board; not touched here.
4. **Guardian/caregiver fan-out**: `SafeDisclosureService` (khuluma) exists as a
   redaction library but no recipient-expansion path calls it; queue
   notifications go to the patient's own inbox only. SMS/EMAIL need a
   CPID→contact lookup (VITO) that is intentionally not done yet.
5. **Nompilo queue guidance**: no queue-related `guidance_item` seeds exist.
6. **Referral/telemedicine → appointment bridge — MISSING**: neither
   `ReferralPackageService` nor `TelemedicineOrchestrationService` creates a
   booking appointment.
7. **Notification dedup**: Kafka at-least-once + no dedup key can duplicate an
   inbox line on rebalance.
8. **Late-arrival policy**: no automatic late/no-show rules; no-show is a manual
   staff action.
9. `pharmacy`/`lab` sub-queues, wrong-queue detection, and analytics
   (bottlenecks, load) are surfaced only via existing queue stats.

## Demo script (compose stack)

1. Sign in as facility staff; select facility/workspace; start shift (`/v1/work/start`).
2. Create an appointment (`POST /internal/v1/appointments`) for a registered patient; confirm it; observe APPOINTMENT_CITIZEN_CONFIRMED inbox message.
3. Check in (`POST /internal/v1/appointments/{id}/check-in`): expect `queue_linked=true`, a queue token, journey with `appointment_id`, QUEUE_CITIZEN_JOINED inbox message.
4. Walk-in second patient via `POST /internal/v1/queue/entries` with `priority: "URGENT"` → token 2, priority 4.
5. Triage walk-in: `/entries/{id}/triage` → 200, status `IN_TRIAGE` (regression: no 500).
6. Escalate the first patient: `/entries/{id}/escalate` with a reason → priority ≥ 5, Tshepo audit row, QUEUE_CITIZEN_PRIORITISED message (no reason leaked).
7. `POST /v1/queues/{id}/call-next` → escalated patient first; QUEUE_CITIZEN_CALLED message.
8. Start encounter from the journey; complete the queue item → QUEUE_CITIZEN_COMPLETED.
9. Mark the walk-in NO_SHOW → journey terminal `NO_SHOW`, QUEUE_CITIZEN_NO_SHOW message.
