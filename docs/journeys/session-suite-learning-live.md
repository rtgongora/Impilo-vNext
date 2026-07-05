# Session Suite — LEARNING_LIVE (Runbook)

Fundo-owned live classroom over an Impilo Live event whose media room is an
rtc-gateway LEARNING_LIVE-template session. Completion is driven by **media
truth** (webhook-accurate attendance), never client-reported progress. Proven
end-to-end on the live preview estate (2026-07-05/06).

## What the mode is (doctrine)

Template: [`contracts/schemas/session-templates/learning-live.json`](../../contracts/schemas/session-templates/learning-live.json)
(`sessionMode: LEARNING_LIVE`, `owningService: FUNDO`, live mode `WEBINAR_CPD`).

- **Layout** `classroom`, join flow `SCHEDULED_CHECKIN`, no lobby.
- **Roles → grants**: FACILITATOR / COFACILITATOR (publish + roomAdmin),
  LEARNER (publish — a classroom, not a broadcast), SUPERVISOR
  (subscribe-only + data), OBSERVER (subscribe-only, hidden).
- **Recording**: on by default, facilitators only, sensitivity `PROFESSIONAL`,
  artifact owner FUNDO.
- Token TTL 10800s, max 100 participants, room prefix `impilo-learn`, audit
  depth `FULL`, post-session
  `REPLAY_PUBLISH / LMS_ASSET / ATTENDANCE_MAP / CERT_ELIGIBILITY`.
- Completion criteria ref: Fundo `lrn_completion_rule`.
- Notification keys: `learning.session.reminder`,
  `learning.session.attendance-recorded`, `learning.completion.achieved`,
  `learning.recording.published`.

## The journey

**Actors**: facilitator (`dr.mapfumo` / PROV-ZW-00001), learner
(`nurse.chienda` / PROV-ZW-00007).

**Surfaces**: learning studio (course, completion rules, session scheduling);
classroom at `/learning/sessions/{id}/classroom` (web); live classroom screens
on both mobile apps (`mobileParity: FULL` for live join).

**Services & events**:

1. learning-service studio APIs create the course, attach an
   `ATTENDANCE_THRESHOLD` completion rule, and schedule a `LIVE` session —
   `LiveSessionIntegration` schedules the linked Impilo Live event
   (`lrn_scheduled_learning_session.live_event_id`, V027).
2. live-service provisions the media room via rtc-gateway with the event's
   `SessionMode` (`LiveMode.WEBINAR_CPD → LEARNING_LIVE`), so learners get
   classroom-native publish grants.
3. LiveKit webhooks → rtc-gateway → `impilo.rtc.*` → live-service records
   server-side attendance in `live.live_event_attendance` (W0 consumer).
4. Room finished → live event `ENDED` → `impilo.live.attendance/event` events
   → learning-service maps rows into `lrn_session_attendance`, evaluates the
   completion policy, transitions the enrolment to `COMPLETED`, and issues the
   certificate.

## Proving it on the preview estate

```bash
bash scripts/e2e/learning-live-proof.sh          # 10 checks
```

The proof provisions everything via APIs, holds REAL two-browser media in the
classroom (`e2e/classroom-media-hold.spec.ts`), then asserts each link of the
chain in the databases: attendance recorded server-side → event `ENDED` →
attendance mapped into Fundo → enrolment `COMPLETED` by the
`ATTENDANCE_THRESHOLD` policy → certificate issued. There is no
client-reported attendance and no blind `progress=100` anywhere.

An interactive variant is `e2e/learning-live-classroom.spec.ts` (two-persona
classroom join + roster assert).

## Key contracts

- Attendance truth lives in live-service (`live_event_attendance`), stamped
  from rtc webhooks; Fundo consumes it — never raw `impilo.rtc.*` (layering
  law).
- Completion rules are data (`lrn_completion_rule`); a course with rules
  completes **only** through policy evaluation.
- `completeIfEligible` on the rules path idempotently issues the certificate
  (`c6e55aa04`).

## Failure modes seen and their fixes

| Failure | Fix |
|---|---|
| live-service hardcoded `sessionType: LIVE_EVENT` for ALL rooms — learners were minted subscribe-only broadcast tokens | provision with the event's `SessionMode` via `LiveMode.sessionMode()` (`73779289e`) |
| Legacy role names refused by the new templates | role aliases for LEARNING_LIVE/MEETING/clinical rooms (`cd0f1dcea`) |
| LiveKit v1.8.4 ↔ livekit-client 2.19 protocol-17 skew — 15s NegotiationError/resume churn in every room | server v1.13.3 + egress v1.13.0 (`eebc7272b`); found via instrumented churn diagnostics (`c0cc4cb4f`) |
| Webinar scheduling 500'd on String jsonb columns | `@JdbcTypeCode` mapping (`3f707a7b3`) |
| Fundo webinar scheduling missing the organiser | set organiser (`c16f3a624`) |
| Preview live-service used `LocalDevMediaProvider` (fake media) | real rtc-gateway media provider (`15ac3cc02`) |
| learning-service → live calls missing v1.1 companion headers | full header set (`976861908`, `0cf915726`) |
| Token request shape drift vs the rtc contract | wrap the participant (`ab781fda4`) |
| Live media tokens re-minted per render → reconnect storms | page-lifetime-stable tokens (`e72b4c674`) |
| `room_started` webhook racing the provisioning tx (LiveKit v1.13) | bounded retry (`19ef4e510`) |

## Known limits

- The e2e classroom hold must dismiss the **Switch Context** overlay that the
  shell raises over the classroom (`f8ba9aa5d`, `a2f19af51`) — the UX itself is
  a carry-forward item.
- ASR transcripts are not generated (see the LEARNING_RECORDING runbook).
