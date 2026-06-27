# Khuluma — Wave-1 manual audio/video QA checklist

Live audio/video media is not browser/CI-automatable, so the automated E2E
(`KhulumaWave1E2ETest` + `RealtimeGatewayTest`) proves the **signalling + lifecycle**
(ring → accept → signal → end) and the realtime fan-out, while this checklist supplements
it with the **media** verification a human must perform once rtc-gateway + LiveKit are
configured (`ops/runtime/docker-compose.operations.yml`, LiveKit on 7880–7882).

## Preconditions
- [ ] khuluma-service up (`:8390/actuator/health` = UP) with Postgres + Redis reachable.
- [ ] rtc-gateway-service up (`:8195`) with LiveKit enabled (NOT dev-mode) — API key/secret/URL set.
- [ ] experience-bff up (`:8160`) with `KHULUMA_BASE_URL` pointing at khuluma-service.
- [ ] Two authenticated sessions (User A, User B) in the same tenant; both members of one conversation.

## Audio call (1:1)
1. [ ] A opens the conversation → taps **Call**. A sees an outgoing-call modal; status `RINGING`.
2. [ ] B receives the incoming-call prompt (web bottom-right / mobile sheet) within ~2 s.
3. [ ] B taps **Accept** → call status flips to `ACTIVE` on both ends.
4. [ ] **Two-way audio** is audible both directions; no excessive latency (<400 ms) or echo.
5. [ ] Mute on A → B hears silence; unmute → audio resumes.
6. [ ] A taps **End** → both ends return to the conversation; status `ENDED`.
7. [ ] B declines a second call → A sees `DECLINED`; no media session lingers.

## Video call (1:1)
8. [ ] Start a `VIDEO` call → both participants' camera tiles render.
9. [ ] Camera toggle hides/shows the local tile for the remote peer.
10. [ ] Network blip (toggle Wi-Fi briefly) → connection recovers or surfaces a clear error,
       and the call can be re-established.

## Honest degradation (media unconfigured / consent-gated)
11. [ ] With LiveKit disabled (dev-mode) the call still rings/accepts/ends, and the UI shows the
       **"media unavailable"** state (no fake "connected") — never a silent failure.

## Governance
12. [ ] A non-member cannot join the call (membership/policy deny).
13. [ ] Each call action (start/accept/decline/end) appears in the audit/outbox stream
       (`khuluma_event_outbox`, `impilo.khuluma.call.*`).

## Cross-platform parity
14. [ ] Web (`/work/comms`, `/my/comms`) and mobile (citizen-app Comms tab) show the same
       conversation, messages, and call lifecycle for the same users.

Record date, build SHA, tester, and any defects below.

| Date | Build SHA | Tester | Result | Notes |
|------|-----------|--------|--------|-------|
|      |           |        |        |       |
