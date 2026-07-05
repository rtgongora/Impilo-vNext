# Product Truth — Adaptive Real-Time Session Suite (2026-07-06)

> **Status:** All five session modes proven on the live preview estate
> (proof runs 2026-07-05/06). **This document is honest about what is real vs
> what is a seam.** Every claim below is grounded in code or a live-estate
> proof, not file existence.
> Legend: ✅ proven live · 🟡 partial · ⬜ seam (not built).

One LiveKit substrate (server v1.13.3 + egress v1.13.0, in-chart), one
template contract (`contracts/schemas/session-templates/`), five modes.
rtc-gateway owns media sessions/tokens/webhooks/recording; live-service owns
events/attendance/replay; owning services (PCT, FUNDO, KHULUMA, LIVE) own the
business objects. Mode runbooks: `docs/journeys/session-suite-*.md`.

## What is REAL (estate-proven)

| Capability | Status | Evidence |
|---|---|---|
| Template-driven token grants — rtc-gateway refuses roles absent from the mode template | ✅ | all mode proofs; `RtcGatewayService` |
| Two-party real-browser media join + mutual track subscription (TELEMEDICINE) | ✅ | `e2e/session-media-core.spec.ts` |
| Patient waiting-room: no token while WAITING → admit → auto-transition → media | ✅ | `e2e/telehealth-patient-flow.spec.ts` |
| Recording pipeline: policy gate (PATIENT refused) → RoomCompositeEgress → MinIO → `rtc.recordings` COMPLETE → docstore adoption → signed-URL mp4 fetch | ✅ | `scripts/e2e/session-recording-proof.sh` |
| LEARNING_LIVE completion from **media truth**: webhook attendance → event ENDED → `lrn_session_attendance` → ATTENDANCE_THRESHOLD policy → COMPLETED → certificate | ✅ | `scripts/e2e/learning-live-proof.sh` (10 checks) |
| LEARNING_RECORDING chain: governed recording → `PUBLISHED_REPLAY` with artifact payload → `lrn_media_asset` adoption → enrolment-gated signed playback → monotonic watch truth → WATCH_THRESHOLD → COMPLETED → certificate | ✅ | `scripts/e2e/learning-recording-proof.sh` (15 checks) |
| MEETING knock lobby: host auto-admit, participant WAITING, admit → READY → dual publish + screen-share control | ✅ | `e2e/meeting-flow.spec.ts` |
| LIVE_EVENT stage machine: client-asserted SPEAKER clamped to AUDIENCE (JWT `video.canPublish=false` asserted), request-stage → backstage approval → next mint publishes | ✅ | `e2e/live-event-stage.spec.ts` |
| Server-side LIVE_EVENT role resolution (creator ⇒ HOST, else AUDIENCE) | ✅ | `63193913a` + stage proof step 2 |
| `LiveMode.sessionMode()` mapping — every live mode provisions its own template (no more broadcast-token classrooms) | ✅ | `LiveMode.java`; `73779289e` |
| Khuluma meeting chain alive end-to-end (base URLs, PROFESSIONAL_MEETING, KHULUMA owning key, synthesized Idempotency-Key) | ✅ | `96e2c60c8`, `0c54eaecb`, `297bc8437`, `c5a411ca6` |
| Replay ordering convergence (artifact-before-end and end-before-artifact both reach PUBLISHED_REPLAY) | ✅ | `RtcSessionEventsConsumer` guard (`5e0558663`); enriched payload (`db91cf870`) |
| Rule-governed completions issue certificates idempotently | ✅ | `FundoProgressService` rules path (`c6e55aa04`); both learning proofs |
| Webhook provision-commit race tolerated (bounded retry: 3 attempts × 700 ms) | ✅ | `RtcWebhookTranslator.resolveSession` (`19ef4e510`) |
| Mobile: telemedicine waiting-room/call screens (both apps), live classroom (both apps), citizen course player, join-capable MeetingScreen | ✅ | W2/W3/W4/W5 mobile commits; vitest suites |

## Honest seams (verified against code 2026-07-06)

| Seam | Verified reality | Owner / next step |
|---|---|---|
| ⬜ **SIP/PSTN dial-in** | No SIP code exists in rtc-gateway at all; the only artifact is the reserved template flag `fallbackRules.sipFallback` (default false) | rtc-gateway; needs a SIP provider + LiveKit SIP integration |
| ⬜ **ASR transcripts** | `lrn_media_asset.transcript` exists and `TranscriptPane` renders it **when present** (offline-produced); no speech-recognition service anywhere in `services/` | media-intelligence worker consuming `impilo.learning.media.replay_adopted.v1`, if funded |
| ⬜ **Guest/unauthenticated invite identities** | tshepo has no GUEST assurance tier (zero matches); khuluma `MeetingInviteService` resolves invites for **authenticated identities only** (token carries no identity by design) | trust plane defines the guest tier; khuluma then adds a guest resolve path (template GUEST grant already reserved) |
| ⬜ **RTMP stream-out** | API exists but `RTC_STREAM_OUT_ENABLED:false`; no restream targets; egress state not persisted (`RtcStreamOutService`: "start returns the egressId, stop takes it back") | rtc-gateway; durable stream-egress bookkeeping + restream target infra |
| ⬜ **Announcement notification dispatch** | live-service publishes `impilo.live.announcement.published.v1`; notification-service has **no consumer** for the key — in-room delivery only | notification-service consumer + register the template key |
| 🟡 **Demote is next-mint enforcement** | No demote endpoint, no LiveKit force-disconnect / participant update; role changes bind at the next token mint/refresh only | rtc-gateway server-side participant update if hard demote is required |
| ⬜ **Backstage attendance** | Intentionally untracked — the backstage child room is a production workspace, not an attendance surface | deliberate; revisit only if production-staff attendance becomes a requirement |
| ⬜ **Recording retention/lifecycle** | document-service has no retention policy; deletion is possible via the Landela lifecycle call but nothing schedules it for recordings | document-service + governance: define retention classes per `sensitivityClass` |
| ⬜ **Provider-mobile course player** | provider-app has the live classroom screen but no recorded-course player (citizen-app and web have it) | lift `coursePlayerService` into `packages/mobile-learning` when parity is scheduled |
| ⬜ **TLS for external browser media** | `getUserMedia` requires a secure context — external browsers cannot open cameras against the plain-http preview ingress; proofs run VM-local | infra: DNS + certificates on the preview ingress |
| ⬜ **Calendar/ICS invites** | No `text/calendar`/VEVENT generation anywhere | khuluma/notification follow-up |

## What preview proves vs what production still needs

- Proofs run **VM-local** with fake media devices; real-device, real-network
  (TURN/TLS) behaviour is unproven pending the TLS/DNS infra item.
- The notification template batch
  (`scripts/operator/register-session-notification-templates.sh`) registers
  the 7 dispatched keys; the templates declare more keys than are wired (see
  `docs/runbooks/session-notification-templates.md`).
- `KHULUMA_MEETING_INVITE_SECRET` is a preview chart value; production needs a
  secret store.
- Preview pipeline remains not production-safe (existing truth record
  stands).
