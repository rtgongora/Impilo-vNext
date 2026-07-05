# W5 — MEETING session (Khuluma over Impilo Live / rtc-gateway)

**Status**: built + unit/IT proven (2026-07-05); live-estate proof pending (see checklist at the end).

A professional meeting is a **Khuluma MEETING conversation** (chat, membership, notes, action
items) composed over an **Impilo Live event** (scheduling + attendance SoR) whose media room is
an **rtc-gateway MEETING-template session** (`contracts/schemas/session-templates/meeting.json`:
HOST/COHOST roomAdmins, PARTICIPANT publish, OBSERVER subscribe-only, KNOCK lobby, grid layout,
host/cohost-only recording). No new SoR was created.

## Ownership map

| Concern | Owner | Khuluma's relationship |
|---|---|---|
| Meeting chat/membership/notes/action items/lobby *decisions* | khuluma-service (`khuluma_meeting_admissions`, `khuluma_meeting_action_items`, V008) | System of record |
| Event schedule (`scheduledAt` → `startTime`), live attendance | live-service | Pass-through + read-through |
| Media room, tokens, waiting-room transport truth, recording | rtc-gateway (`rtc.session_participants`) | Drives admit/deny; mirrors outcomes; consumes `impilo.rtc.participant.*` for attendance stamps |

Join flow: khuluma `meeting/join` → live-service `room/{event}/join` (provisions the rtc session,
returns `providerRoomId` = rtc session id) → khuluma mints the participant token **directly from
rtc-gateway** with the template role. Non-admin roles get `WAITING` until a host admits
(`meeting/lobby/admit|deny` drive rtc-gateway first, then the khuluma mirror).

## Honest seams (owner / reason / next step)

1. **Unauthenticated GUEST identities are NOT supported.** Invite links
   (`/meet/join?token=…`, HMAC-signed khuluma-side) resolve only for authenticated users.
   *Owner*: trust plane (tshepo). *Reason*: the GUEST assurance tier (LOA for anonymous
   meeting participants, header contract, audit identity) is undefined — building it inside
   khuluma would fork identity truth. *Next step*: tshepo defines a guest/ephemeral actor
   tier; khuluma then adds a guest resolve path that mints a scoped guest actor before the
   knock. The MEETING template's `GUEST` role grant already exists for that day.
2. **Invite-link secret is per-boot unless configured.** `impilo.khuluma.meeting-invite-secret`
   (env `KHULUMA_MEETING_INVITE_SECRET`) unset → random per-boot secret (dev-safe, fails
   honestly across restarts/replicas). Preview sets a chart value; production must move it to
   a real secret store (owner: platform/deploy lane).
3. **Meeting audit rides the khuluma outbox, not `khuluma_call_events`.** `khuluma_call_events`
   is call-aggregate-scoped (FK to `khuluma_calls`); meetings are conversations, not calls. All
   meeting actions (join, admission decisions, cohost changes, hand/reactions, action items,
   invites) append `impilo.khuluma.meeting.*.v1` outbox events — the same durable audit +
   Kafka trail the rest of khuluma uses.
4. **Mobile is JOIN_CAPABLE (per template `mobileParity`).** Citizen app joins, waits in the
   lobby, publishes, raises hands and reacts. Web-only this wave: lobby moderation, cohost
   management, invite minting, agenda/notes editing, attendance summary.
5. **Lobby knock-notification delivery.** `meeting.admission.requested` reaches open host
   clients via the khuluma realtime gateway + a 4s lobby poll; the template's
   `khuluma.meeting.admission-requested` notification key is NOT yet wired into
   notification-service push (owner: khuluma/notifications; next step: dispatch the key from
   the admission-requested outbox event).
6. **Degraded fallback path.** If live-service cannot report the rtc session id, join falls
   back to the legacy live-service token path (media may still work; lobby state is
   unavailable that way). `status=UNAVAILABLE` is surfaced honestly, never faked.

## Live-estate proof checklist (coordinator)

- `e2e/meeting-flow.spec.ts` (orchestrated-only): host knock/admit/dual-publish/screen-share
  against preview; requires the scenario-A media personas and `KHULUMA_MEETING_INVITE_SECRET`.
- Attendance stamps require khuluma's Kafka listeners on (`SPRING_KAFKA_LISTENER_AUTO_STARTUP`)
  and rtc webhooks flowing (already true in preview).
- Recording start rides the existing live `room/{event}/record` endpoint (template gates apply).
