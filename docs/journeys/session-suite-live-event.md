# Session Suite — LIVE_EVENT (Runbook)

Broadcast-tier events (public broadcasts, hybrid events, emergency briefings)
owned by live-service: a subscribe-only audience, a governed stage, a hidden
backstage, and server-resolved role tiers. Proven on the live preview estate
(2026-07-05/06).

## What the mode is (doctrine)

Template: [`contracts/schemas/session-templates/live-event.json`](../../contracts/schemas/session-templates/live-event.json)
(`sessionMode: LIVE_EVENT`, `owningService: LIVE`, live modes
`PUBLIC_BROADCAST / HYBRID_EVENT / EMERGENCY_BRIEFING`).

- **Layout** `stage`, join flow `REGISTRATION`.
- **Roles → grants**: HOST / PRODUCER (publish + roomAdmin; PRODUCER hidden),
  MODERATOR (subscribe + data + roomAdmin), SPEAKER (publish), AUDIENCE
  (subscribe-only, no data publish). **Roles are resolved server-side** — a
  client-asserted role is clamped to what the server derives (creator ⇒ HOST;
  everyone else starts AUDIENCE).
- **Recording**: on by default, HOST/PRODUCER only, sensitivity `GENERAL`.
- Token TTL 14400s, max 500 participants (capacity passed through to the
  provider on room create), room prefix `impilo-live`, moderated chat,
  audit depth `BASIC`, post-session `REPLAY_PUBLISH / CERT_ELIGIBILITY / SUMMARY`.
- Notification keys: `live.event.{registration-confirmed,starting-soon,replay-published,speaker-approved,announcement}`.

## The journey

**Actors**: host/creator, producers backstage, audience members, promoted
speakers.

**Surfaces**: live event landing page with role-aware CTAs; role-tier live
room (audience engagement rail, request-stage button); backstage producer
console (speaker queue, approvals); replay page after publish.

**Services & events**:

1. live-service owns the event (V004: stage requests, chat kind, backstage
   linkage) and provisions the room via rtc-gateway with the event's
   `SessionMode` (`LiveMode.PUBLIC_BROADCAST → LIVE_EVENT`).
2. **Stage machine**: audience member requests the stage → producer approves
   in the backstage console → the participant's **next token mint** carries
   `canPublish: true` (SPEAKER) and the room flips to the stage variant.
3. **Backstage** is a child room linked to the parent session (rtc parent
   session linkage), with role-enforced tokens — audience can never join it.
4. **Announcements** ride the moderated-chat vocabulary and publish
   `impilo.live.announcement.published.v1`.
5. Analytics snapshots enrich with rtc media-quality aggregates; recording +
   replay follow the shared W1 pipeline.
6. The BFF relays stage/backstage/announcement endpoints (`7168a70ca`).

## Proving it on the preview estate

```bash
(cd ui/one-ui-shell && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1 \
  PREVIEW_SANDBOX_E2E=1 npx playwright test e2e/live-event-stage.spec.ts)
```

The spec proves the audience→stage promotion **and the escalation it closes**:

1. Persona A provisions a `PUBLIC_BROADCAST` (typed scheduling endpoint;
   creator ⇒ server-resolved HOST) and goes live.
2. Persona B token-probe: a client-asserted `SPEAKER` request yields a
   subscribe-only grant — the minted JWT literally carries
   `video.canPublish=false` and the response echoes the clamped `AUDIENCE`
   role.
3. B's room UI is viewer-mode (no publish controls) with a request-stage
   button; B requests.
4. A approves from the backstage producer console's speaker queue.
5. B's next token mints `canPublish=true` (SPEAKER); the room shows
   "On stage".

The proof provisions a **standalone broadcast and schedules before go-live**
(`98ff9415d`) — going live first races the scheduling contract.

## Key contracts

- Role tiers are **server-resolved** (`63193913a`); the JWT grant is the
  enforcement artifact, asserted by decoding `video.canPublish` in the proof.
- Room capacity and parent-session linkage pass through provisioning
  (`ac2a6a07c`).
- RTMP stream-out API exists but is **flagged off**
  (`RTC_STREAM_OUT_ENABLED:false`) — see seams.

## Failure modes seen and their fixes

| Failure | Fix |
|---|---|
| Client-asserted SPEAKER role escalated to publish rights | server-side LIVE_EVENT role resolution + clamped grants (`63193913a`) |
| All modes previously inherited this template's subscribe-only AUDIENCE behaviour (hardcoded `LIVE_EVENT` sessionType) | `LiveMode.sessionMode()` mapping (`73779289e`) |
| Stage proof raced go-live vs scheduling | provision standalone broadcast, schedule before go-live (`98ff9415d`) |

## Known limits (honest seams)

- **Demote is next-mint enforcement** — an approved speaker demoted back to
  audience keeps publishing until their current token expires or they
  reconnect; there is no LiveKit force-disconnect / server-side participant
  update.
- **Backstage attendance is intentionally untracked** — the backstage child
  room is a production workspace, not an attendance surface.
- **RTMP stream-out**: API is flagged off by default, no restream targets
  exist, and egress state for stream-out is not persisted
  (`RtcStreamOutService` — start returns the egressId, stop takes it back).
- **`live.event.announcement` dispatch**: the event is published
  (`impilo.live.announcement.published.v1`) but notification-service has no
  consumer for it — announcements reach in-room surfaces only.
