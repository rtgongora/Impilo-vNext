# Session Suite — MEETING (Runbook)

A professional meeting is a **Khuluma MEETING conversation** (chat,
membership, notes, action items, lobby decisions) composed over an **Impilo
Live event** (scheduling + attendance SoR) whose media room is an
**rtc-gateway MEETING-template session**. No new system of record. Proven on
the live preview estate (2026-07-05/06). Architecture + ownership map:
[`docs/architecture/MEETING_SESSION_W5.md`](../architecture/MEETING_SESSION_W5.md).

## What the mode is (doctrine)

Template: [`contracts/schemas/session-templates/meeting.json`](../../contracts/schemas/session-templates/meeting.json)
(`sessionMode: MEETING`, `owningService: KHULUMA`, live mode
`PROFESSIONAL_MEETING`).

- **Layout** `grid`, join flow `LOBBY` with `KNOCK` behaviour — non-admin
  roles wait until a host admits.
- **Roles → grants**: HOST / COHOST (publish + roomAdmin), PARTICIPANT and
  GUEST (publish), OBSERVER (subscribe-only + data). The GUEST grant exists in
  the template but is **not reachable** today (no guest identity tier — see
  seams).
- **Recording**: off by default, HOST/COHOST only, sensitivity
  `PROFESSIONAL`, artifact owner KHULUMA.
- Token TTL 7200s, max 50 participants, room prefix `impilo-meet`, chat
  persistence `CONVERSATION` (the meeting IS a khuluma conversation), audit
  depth `BASIC`, post-session `SUMMARY / FOLLOW_UP`.
- Notification keys: `khuluma.meeting.{invite,admission-requested,starting-soon,action-item-assigned,recording-ready}`.

## The journey

**Actors**: host (`dr.mapfumo`), participant (`nurse.chienda`); invitees via
HMAC-signed links.

**Surfaces**: web `/meet/{id}` (lobby, grid room, participants panel with
admit queue, reactions, invite minting, recording notice, summary); CommsHub
meetings open the dedicated room; mobile `MeetingScreen` (JOIN_CAPABLE: join,
knock, publish, hand + reactions).

**Services & events**:

1. khuluma-service owns the meeting (V008 `khuluma_meeting_admissions`,
   `khuluma_meeting_action_items`) and schedules a `PROFESSIONAL_MEETING`
   live event with `owningService: KHULUMA`.
2. Join: khuluma `meeting/join` → live-service `room/{event}/join` (provisions
   the rtc session) → khuluma mints the participant token **directly from
   rtc-gateway** with the template role. Non-admins get `WAITING` until a host
   admits; admit/deny drives rtc-gateway first, then the khuluma mirror.
3. Attendance stamps ride `impilo.rtc.participant.*`; all meeting actions
   append `impilo.khuluma.meeting.*.v1` outbox events (durable audit trail).

## Proving it on the preview estate

```bash
(cd ui/one-ui-shell && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1 \
  PREVIEW_SANDBOX_E2E=1 npx playwright test e2e/meeting-flow.spec.ts)
```

The spec drives the full KNOCK-lobby journey: host provisions + auto-admits
(roomAdmin bypass) → participant lands in the lobby `WAITING` → host admits
from the participants panel → participant's join poll flips `READY` → both
publish real (fake-device) media until each layout shows both participants,
plus the host screen-share control assert. Requires the scenario-A personas
and `KHULUMA_MEETING_INVITE_SECRET` set (preview chart value).

## Key contracts

- Invite links (`/meet/join?token=…`) are HMAC-signed khuluma-side
  (`MeetingInviteService`); the token carries **no identity** — resolving it
  joins the caller's authenticated identity with the embedded role.
- Khuluma synthesizes the v1.1 `Idempotency-Key` (+ pod headers) on every
  outbound command (`ServiceHttpConfig`, `c5a411ca6`) — fresh key per
  attempt, since khuluma commands are domain-idempotent.
- Degraded fallback: if live-service cannot report the rtc session id, join
  falls back to the legacy live token path and surfaces
  `status=UNAVAILABLE` honestly.

## Failure modes seen and their fixes

The meeting chain was **dead-by-construction in three layers** — each alone
made meetings silently media-less, and only live-estate proofs surfaced them
(unit suites were green):

| Failure | Fix |
|---|---|
| khuluma's live/rtc base URLs defaulted to localhost in preview — meetings silently no-media | preview chart wiring (`96e2c60c8`) |
| Meetings scheduled live events with mode `VIRTUAL` — no MEETING template mapped | schedule `PROFESSIONAL_MEETING` (`0c54eaecb`) |
| `owningService: khuluma-service` didn't match the canonical `KHULUMA` key — consumers filtered it out | canonical owning service (`297bc8437`) |
| Outbound khuluma POSTs missing `Idempotency-Key` → downstream v1.1 filter rejected them | synthesize pod + idempotency headers (`c5a411ca6`) |

## Known limits (honest seams)

- **No unauthenticated GUEST join** — the tshepo guest assurance tier is
  undefined; invite resolve is authenticated-only. The template's GUEST grant
  awaits that trust-plane work.
- **Invite-link secret is per-boot unless configured**
  (`KHULUMA_MEETING_INVITE_SECRET`); production needs a real secret store.
- **Knock notifications** reach open host clients via the realtime gateway +
  lobby poll; the `khuluma.meeting.admission-requested` notification key is
  not yet dispatched through notification-service.
- **No calendar/ICS invites** — nothing generates `text/calendar` artifacts.
- Mobile is JOIN_CAPABLE per template; lobby moderation, cohost management,
  invite minting, agenda/notes, attendance summary are web-only this wave.
