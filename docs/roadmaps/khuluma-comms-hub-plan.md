# Khuluma — Impilo Comms Hub: implementation plan (durable brief)

Native, internet-first communication & coordination layer ("the human-interaction bus"): unified
conversations, presence, calls/meetings, escalation + notification orchestration, future omnichannel
adapters — under Tshepo-governed, audited trust. External channels (SMS/WhatsApp/email/IVR) are future
**adapters**, never required for core. Media = **LiveKit (self-hosted, canonical)**; no custom WebRTC.

Branch: `intake/khuluma-comms-hub` (off `intake/wave-b`). Service: `khuluma-service` (port **8390**).

## Reuse map (audit — do NOT duplicate these SoRs)
- `channels-service` (8130): channel sessions + messages, delivery_status, escalate, inbound webhook.
- `notification-service` (8200): notifications inbox (PENDING→SENT→DELIVERED→READ), templates, receipts,
  preferences, providers (smtp/http-sms + stubs), Kafka listeners. = notification/inbox SoR + adapters.
- `live-service` (8380): live events/webinars (registration/attendance/Q&A/chat/polls/certs); LiveKit room/session. = meetings/live SoR.
- `rtc-gateway-service` (8195/8196) + LiveKit (deployed `ops/runtime/docker-compose.operations.yml` 7880-7882):
  room provisioning + participant tokens + consent gate; `rtc.rtc_sessions`. = calls/meetings media SoR.
- `pct-service`: telemedicine orchestration (encounter↔session VIDEO/AUDIO/PHONE). = teleconsult SoR.
- UI: `/communication` (+secure-messaging), `/telemedicine`, `/omnichannel`, `NotificationsCommsHub`,
  `ShellNotificationTray` (+ optional WS client `useOptionalNotificationWebSocket.ts`).
- Mobile: `messagingService`/`MessagingInboxScreen`, `telehealthService`/`LiveKitMobileConsultRoom`.
- Platform patterns to mirror: PolicyEngine + `infra/opa/impilo/*.rego`; `AuditPublisher.queueGovernanceEvent`
  outbox→Kafka; `data-governance-service` scaffold; `VashandiServiceClient`/`ServiceEndpoints` + companion headers;
  gates `scripts/guard/*` + `ui/one-ui-shell/src/lib/routes.ts`.

## Genuine gaps (BUILD in khuluma-service)
Realtime push transport (none today bar a niche `FetalMonitoringStreamService` SSE); presence; unified
conversation index + linked-object model + escalation/SLA; first-class unified Comms Hub shell surface.

## Architecture decisions (PO-approved)
- New `khuluma-service` owns ONLY the missing domains; orchestrates/reuses the SoRs above.
- Realtime gateway = **BOTH SSE and WebSocket** over one subscription/dispatch core backed by **Redis pub/sub**
  (multi-instance fan-out): `GET /sse` (EventSource; reuse FetalMonitoring SSE precedent) + `/ws` (the frontend
  client hook exists). Policy-aware; tied to khuluma conversation/call records.
- Media via rtc-gateway + LiveKit (reuse `POST /sessions` + `/participants/token`); khuluma adds call context +
  ringing/lifecycle + escalation links.

## Wave 1 — vertical slice (acceptance E2E: 2 users realtime-message + 1:1 call + notifications + presence + policy-deny + audit)
- **W1.1 DONE** — scaffold (pom/Application/SecurityConfig/application.yml/V001 outbox+idempotency, module + port). Compiles.
- **W1.2** — V002 domain migration + entities/repos: `khuluma_conversations`, `_conversation_participants`,
  `_messages`, `_message_receipts`, `_conversation_links`, `_presence`, `_calls`, `_call_participants`, `_call_events`.
  Indexes (inbox, participants, unread, presence, calls-by-participant, linked-object). + `ConversationService`,
  `MessageService` (send/list/mark-read+receipts), `PresenceService`, `CallService` (start/ring/accept/decline/end +
  LiveKit token via rtc-gateway client), outbox events, audit. Unit tests + v11 probe controller.
- **W1.3** — realtime gateway (SSE + WS, Redis pub/sub fan-out): message/notification/presence/ringing/typing events. Tests.
- **W1.4** — policy: `infra/opa/impilo/khuluma.rego` + policy_rules seed; BFF prechecks (mirror `ImagingAccessPolicyService`).
- **W1.5** — BFF: `KhulumaServiceClient` (mirror `VashandiServiceClient`) + `KhulumaController` `/internal/v1/khuluma/**`
  (summary, inbox, conversations, messages, notifications, unread-count, presence, calls, calls/{id}/signals) + `/internal/v1/mobile/khuluma/**`. MockMvc tests.
- **W1.6** — web UI: `/work/comms` + `/my/comms` (inbox, conversation list/detail, send-message realtime, notifications
  + badge, presence control, incoming-call modal + 1:1 LiveKit call). Register in `routes.ts`; wire the optional-WS client. RTL tests.
- **W1.7** — mobile: Comms Hub home/inbox/conversation/notifications/incoming-call (reuse `LiveKitMobileConsultRoom`). Tests.
- **W1.8** — E2E smoke (spec §E2E) + manual audio/video QA checklist; registry entry (`services-registry.yaml` +
  `system-of-record-map.md`); compose/helm + health; gates (parity/no-stubs/product-truth) green; Wave-1 report.

## Later waves
W2 meetings/live (reuse live-service) · W3 teleconsultation (reuse pct, encounter handoff) · W4 escalation/routing/SLA
(hooks: Nhume/referrals/oros/vashandi-on-call) · W5 facility/programme channels + moderated communities + broadcast ·
W6 adapter abstraction (channels/adapters/delivery_attempts; native-first, push/SMS/WhatsApp/email/USSD/IVR honest
configured/not-configured reusing notification providers) · W7 presence depth + vashandi on-duty/on-call · W8 full UI
breadth + admin/governance + mobile parity · W9 Product Truth + gates + program report.

## Cadence / gates
Coherent verified slices; `mvn -q -o test` (khuluma + experience-bff), `tsc`/`vitest` (one-ui-shell + mobile),
SSE+WS realtime tests; gates `check-backend-frontend-parity.sh`/`check-route-inventory.sh`/
`check-frontend-mocks-and-stubs.sh`/`check-product-truth.sh`. Atomic commit per slice on this branch via worktree.

## Honest boundaries
External SMS/WhatsApp/email/IVR = adapters, not required (reuse notification provider seam; honest
configured/not-configured). LiveKit = the "native" self-hosted media layer. Live audio/video media isn't
browser-automatable → manual QA checklist supplements the automated signalling/lifecycle E2E.
