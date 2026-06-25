# Khuluma — real calls + messaging runbook (bring it up)

How to get **real messaging, audio/video calls, and virtual meetings** running on the
self-hosted stack — no commercial provider. Everything below is wired with dev defaults, so a
plain bring-up works; the env knobs are only for overrides.

## One-command bring-up (canonical)
```bash
./scripts/runtime/platformctl.sh up lite
./scripts/runtime/platformctl.sh bootstrap   # keycloak realm, seed, etc.
./scripts/runtime/platformctl.sh smoke
```
This brings up (on the shared `impilo-network`): postgres, redis, kafka, keycloak, **livekit**
(7880–7882), **rtc-gateway** (8195), **khuluma-service** (8390), **live-service** (8380),
**experience-bff** (8160), **one-ui-shell** (3000), pct, and the rest.

> Upgrading an existing Postgres volume? The new `impilo_khuluma` + `live` databases are created by
> the one-shot `postgres-db-ensure` service (and `scripts/seed/ensure-databases.sh`); a fresh volume
> gets them from `scripts/seed/init-databases.sql`.

## The call/media chain (all on impilo-network)
```
browser → one-ui-shell(3000) → experience-bff(8160) → khuluma-service(8390)
        → rtc-gateway(8195) → livekit(7880, published to host for the browser)
khuluma-service → live-service(8380) → rtc-gateway → livekit          (meetings)
pct-service → rtc-gateway → livekit                                    (teleconsult)
```

## Verify it's live
1. **Health:** `curl -s localhost:8390/actuator/health` (khuluma), `:8380` (live), `:8195` (rtc-gateway), `:7880` (livekit), `:8160` (bff) → all `UP`.
2. **Backend smoke (full §E2E journey):** `BASE=http://localhost:8390 scripts/e2e/khuluma-wave1-smoke.sh`
   (A↔B converse → read receipt → call ring/accept/signal/end → membership deny).
3. **Web:** open `http://localhost:3000`, sign in as two users (two browsers/profiles), open
   **Khuluma** (`/work/comms` or `/my/comms`):
   - send a message → the other side sees it within ~4 s (poll) or instantly (if `NEXT_PUBLIC_KHULUMA_WS` is set);
   - **Call / Video** → the other user gets the incoming-call prompt (≤4 s poll) → Accept → two-way audio/video over LiveKit;
   - **Meet** → multi-party virtual meeting room.
4. **Real media (manual):** [docs/qa/khuluma-wave1-av-qa-checklist.md](../qa/khuluma-wave1-av-qa-checklist.md).

## Env knobs (all defaulted; override only to change)
| Service | Var | Default | Purpose |
|---------|-----|---------|---------|
| rtc-gateway | `LIVEKIT_ENABLED` / `LIVEKIT_URL` / `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | `true` / `http://livekit:7880` / `devkey` / `secret` | real LiveKit tokens (set a strong key/secret for prod) |
| rtc-gateway | `RTC_DEV_MODE_ENABLED` | `false` | keep false to mint real tokens |
| khuluma / live / pct | `RTC_GATEWAY_BASE_URL` | `http://rtc-gateway:8195` | reach the media gateway |
| live-service | `LIVE_MEDIA_PROVIDER` | `rtc-gateway` | real meeting media (vs `local-dev` stub) |
| pct-service | `PCT_TELEMED_DEFAULT_PROVIDER` | `RTC_GATEWAY` | consults on the same media path |
| one-ui-shell | `NEXT_PUBLIC_KHULUMA_WS` | (unset) | optional: instant realtime push; unset → secure poll (4 s) still rings/delivers |

## How ringing works on push (no socket required)
The browser can't open the realtime gateway directly (the companion filter gates it, and
query-param identity would allow impersonation), so the incoming-call prompt comes from a secure
**4 s poll** through the authenticated BFF (`GET /internal/v1/khuluma/calls/incoming`). Messages
arrive live the same way (inbox 5 s, open conversation 4 s). When `NEXT_PUBLIC_KHULUMA_WS` is wired
to a BFF/Envoy-proxied gateway path, the WebSocket delivers instantly and the poll is just a backstop.

## Known prod hardening (not blocking dev)
- Replace LiveKit `devkey/secret` with strong credentials; terminate LiveKit TLS (7881).
- Front the realtime gateway with Envoy (header injection) for instant browser push in prod.
- `RTC_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA=true` stays on; Khuluma supplies a call/encounter consent
  reference. For clinical patient-linked calls, wire a real tshepo-consent reference.
