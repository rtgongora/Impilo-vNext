# Impilo Live — Live Events, Webinars & Broadcast

> Sovereign live events domain for Impilo vNext. Owns event truth; delegates media to `rtc-gateway-service`.

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
- **Notifications**: registration/schedule reminders via `/internal/v1/notify`
- **Nompilo**: event explain, agenda, replay summary, follow-up recommendations

## Permissions

All requests via Envoy → TSHEPO ext_authz. Trust headers required. Event visibility: PUBLIC, ROLE_BASED, FACILITY, INVITE_ONLY. Clinical case events restricted to professional groups.

## Web routes

- Work: `/live`, `/live/manage`, `/live/create`, `/live/event/[eventId]/room`
- Professional: `/live/cpd`, `/live/certificates`
- Citizen: `/live/discover`, `/live/saved`, `/live/my-events`, `/live/replays`

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
