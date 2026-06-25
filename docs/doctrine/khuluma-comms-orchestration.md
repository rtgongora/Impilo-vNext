# Khuluma — the Comms Orchestration umbrella

**Doctrine line:** All communication & coordination in the Health OS is *under Khuluma orchestration*.
Khuluma is the named orchestrator of comms — exactly as **Vashandi** is the orchestrator of workforce
operations. Like Vashandi (which orchestrates varapi / tuso / workforce-governance without absorbing
them), Khuluma **orchestrates the comms systems-of-record without bundling or renaming their service
folders**. One brand, one experience umbrella, many governed systems-of-record underneath.

## What Khuluma OWNS (system-of-record)
`khuluma-service` (8390): the unified conversation index, conversation participants, messages + read
receipts, presence, conversation↔canonical-object links, escalation/SLA, and the realtime push
gateway (SSE + WebSocket). It also owns the **orchestration** of calls, meetings, and teleconsults
over the shared media path.

## What Khuluma ORCHESTRATES (reused SoRs — never duplicated, never renamed)
| Capability | System-of-record | How Khuluma uses it |
|-----------|------------------|---------------------|
| Channel sessions + messages | `channels-service` (8130) | omnichannel adapter routing (later wave) |
| Notification inbox, templates, delivery, providers | `notification-service` (8200) | delegated — Khuluma never re-implements notifications |
| Live events / webinars / meetings | **Impilo Live** (`live-service`, 8380) | `MeetingService` orchestrates create/join/token; a Khuluma meeting **is** an Impilo Live event |
| Call / meeting media (rooms + tokens) | `rtc-gateway-service` (8195) + self-hosted **LiveKit** (7880–7882) | `RtcGatewayClient` provisions rooms + mints real LiveKit tokens; no custom WebRTC |
| Teleconsultation (provider↔patient) | `pct-service` | `PctRtcGatewaySessionProvider` runs consults on the same LiveKit media path |
| Communities | `community-service` | facility/programme channels + moderated communities (later wave) |

## Surfaces under the Khuluma umbrella
- **Web** (`ui/one-ui-shell`): `/work/comms` (provider) + `/my/comms` (citizen) are the first-class
  Khuluma Comms Hub. The legacy `/communication` (+ secure-messaging) surfaces and the
  `NotificationsCommsHub` / `ShellNotificationTray` are part of the same Khuluma comms experience.
- **Mobile** (`apps/mobile/citizen-app`): the Comms tab is the Khuluma surface.
- **BFF** (`experience-bff`): `/internal/v1/khuluma/**` + `/internal/v1/mobile/khuluma/**`; the existing
  `CommunicationController` / `Comms*Controller` surfaces compose into the same Khuluma umbrella.

## Media reality (self-hosted, no commercial provider)
Audio call, video call, virtual meeting, and teleconsult all run on **self-hosted LiveKit** via
rtc-gateway (`ops/runtime/docker-compose.operations.yml`). They are real, not best-effort — the only
reason media degrades to "unavailable" is the ops stack being down. To run real media:
- rtc-gateway: `LIVEKIT_ENABLED=true`, `LIVEKIT_URL=http://livekit:7880`, API key/secret set (devkey/secret in dev).
- khuluma-service: `RTC_GATEWAY_BASE_URL=http://rtc-gateway:8195`.
- live-service: `LIVE_MEDIA_PROVIDER=rtc-gateway`, `RTC_GATEWAY_BASE_URL=http://rtc-gateway:8195`.
- pct-service: `PCT_TELEMED_DEFAULT_PROVIDER=RTC_GATEWAY`, `RTC_GATEWAY_BASE_URL=http://rtc-gateway:8195`.

## Khuluma ↔ Impilo Live composition (one product family, not parallel surfaces)
Khuluma meetings and **Impilo Live** events are the **same object**, reachable from either experience —
Khuluma never builds a parallel meeting/event surface:
- **Khuluma → Impilo Live:** `MeetingService.create` makes a private Impilo Live event (`owningService=
  khuluma-service`, default visibility `PRIVATE`, status `DRAFT` → it does **not** appear in the public
  Impilo Live discover feed, which lists `SCHEDULED` events by context). The Khuluma "Meet" affordance is
  just the quick-entry; the conversation carries the chat thread + membership. The web Comms Hub shows
  **Open in Impilo Live** (`/live/event/{eventId}`) for any meeting conversation, so the rich Impilo Live
  experience (registration, attendance, Q&A, polls, CPD, certificates, replay) is one click away.
- **Impilo Live → Khuluma:** any existing Impilo Live event can gain a Khuluma discussion thread +
  quick-join, idempotently, via `POST /internal/v1/khuluma/meetings/from-event {eventId}` and
  `GET /internal/v1/khuluma/events/{eventId}/conversation`. The Impilo Live screens
  (`LiveEventScreen`, web `/live/event/[eventId]`) anchor the Khuluma chat through these (web
  `useMeetingActions().fromEvent/eventConversation`, mobile `khulumaCommsService.meetingFromEvent/eventConversation`).

Impilo Live remains the system-of-record for the live-event registry, scheduling, discovery, registration,
attendance, Q&A/polls, CPD and certificates; Khuluma owns only the conversation, presence, and the
conversation↔event link.

## Boundary (anti-duplication)
Khuluma must not become a second notification inbox, channel-message store, live registry, or media
SoR. It coordinates; the SoRs above remain authoritative. See
[`docs/registry/system-of-record-map.md`](../registry/system-of-record-map.md) and the
`forbidden_responsibilities` on `khuluma-service` in
[`docs/registry/services-registry.yaml`](../registry/services-registry.yaml).
