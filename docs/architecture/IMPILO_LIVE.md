# Impilo Live — Live Events, Webinars & Broadcast

> **Impilo Live is the governed live engagement layer of the Impilo Health OS**, enabling clinical sessions, professional meetings, CPD webinars, public health broadcasts, hybrid events and emergency briefings through one secure, role-aware, consent-aware and analytics-enabled service.

> Sovereign live events domain for Impilo vNext. Owns live interaction capability; delegates media to `rtc-gateway-service`. Telemedicine owns clinical care; Fundo owns learning; Public Health owns campaigns.

## Canonical modes & owning services (doctrine)

| Mode | Typical owner | Impilo Live provides |
|------|---------------|----------------------|
| `CLINICAL_SESSION` | Telemedicine/PCT | Governed room, consent, audit — **not** clinical documentation |
| `PROFESSIONAL_MEETING` | Enterprise / MoHCC | Meetings, attendance, chat, recording |
| `WEBINAR_CPD` | Fundo | Webinar room, Q&A, polls, replay signals |
| `PUBLIC_BROADCAST` | Public Health | Moderated public stream, verified speakers |
| `HYBRID_EVENT` | Enterprise | Combined in-person + online participation |
| `EMERGENCY_BRIEFING` | Public Health | Rapid moderated official communications |

## Integration bridges (owning services call Impilo Live)

| Owning service | Entry point | live-service endpoint |
|----------------|-------------|------------------------|
| PCT Telemedicine | `LiveSessionIntegration` on VIDEO session create | `POST /internal/v1/live/clinical-sessions` |
| Fundo (learning) | `createScheduledSession` for VIRTUAL/LIVE | `POST /internal/v1/live/fundo-webinars` |
| Campaigns (Public Health) | `POST /campaigns/{id}/live-broadcast` | `POST /internal/v1/live/public-broadcasts` |
| Experience BFF | `LiveController` proxy | All `/internal/v1/live/**` |

## Overview

Impilo Live supports professional (CPD, training, grand rounds) and citizen (health education, donor mobilisation) contexts. Core transaction: organiser creates event → trust/audience validated → discovery/registration → live join → interactions → attendance → replay/CPD/certificates/analytics.

## Architecture

| Layer | Component | Port |
|-------|-----------|------|
| Experience BFF | `LiveController`, mobile controllers | 8160 |
| Sovereign SoR | `live-service` | 8380 |
| Media adapter | `rtc-gateway-service` (LiveKit) | 8195 |
| Integrations | Vito, Varapi, Tuso, Fundo, Madi, Notifications, LLM | — |

## Data model

Schema `live` with tables: `live_events`, `live_event_role_assignments`, `live_event_registrations`, `live_event_sessions`, `live_event_attendance`, `live_event_questions`, `live_event_chat_messages`, `live_event_polls`, `live_event_poll_responses`, `live_event_resources`, `live_event_feedback`, `live_event_certificates`, `live_event_analytics_snapshots`, `event_outbox`.

## API surfaces

- Web/BFF: `/internal/v1/live/**`
- Citizen mobile: `/internal/v1/mobile/citizen/live/**`
- Provider mobile: `/internal/v1/mobile/provider/live/**`
- Nompilo assist: `/internal/v1/live/composer/assist`
- Contract: `contracts/openapi/impilo-live.openapi.yaml`

## Streaming abstraction

`LiveMediaProvider` interface with `RtcGatewayMediaProvider` (production) and `LocalDevMediaProvider` (dev, clearly labelled). Config: `live.media.provider=rtc-gateway|local-dev`.

## Integrations

- **Fundo**: `linkedFundoCourseId`; CPD completion via `/learning/v11/sessions/live-completion`
- **Madi**: `linkedMadiDriveId`; drive resolution at `/live/events/{id}/madi-drive`
- **Notifications**: registration/schedule reminders via `/internal/v1/notify` (templates: `LIVE_EVENT_SCHEDULED`, `LIVE_EVENT_STARTING`, `LIVE_REPLAY_PUBLISHED` — see `IMPILO_LIVE.md` § Notifications)
- **Nompilo**: event explain, agenda, replay summary, follow-up recommendations

## Permissions

All requests via Envoy → TSHEPO ext_authz. Trust headers required. Event visibility: PUBLIC, ROLE_BASED, FACILITY, INVITE_ONLY. Clinical case events restricted to professional groups.

## Web routes

- Work: `/live`, `/live/manage`, `/live/admin`, `/live/create`, `/live/event/[eventId]/room`
- Professional: `/live/cpd`, `/live/certificates`
- Citizen: `/live/discover`, `/live/saved`, `/live/my-events`, `/live/replays`

## Notifications

Live events enqueue notification-service templates (via live-service outbox → notification integration):

| Template key | Trigger | Audience |
|--------------|---------|----------|
| `LIVE_EVENT_SCHEDULED` | Event scheduled / registration confirmed | Registrants, host |
| `LIVE_EVENT_STARTING` | T-minus reminder before start | Registrants |
| `LIVE_REPLAY_PUBLISHED` | Replay published after ENDED | Registrants, CPD learners |

Clinical sessions inherit telemedicine consent boundaries — no public broadcast notifications for `CLINICAL_SESSION` mode.

## Testing

```bash
cd services/live-service && mvn test
cd ui/one-ui-shell && npm run test:routes && npm run test:no-stubs && npm test
```

## Known limitations

- Full LiveKit egress/recording requires external credentials; dev mode uses labelled local provider.
- Provider mobile host/moderator controls are partial; full moderation on web.
- Multilingual live captions planned; hooks exist via `accessibilityOptions`.

## Roadmap

- RTMP/HLS adapter for sovereign national media server
- Event approval workflow for national broadcasts
- Deep analytics equity dashboards
