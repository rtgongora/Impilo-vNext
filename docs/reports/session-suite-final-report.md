# Final Report — Impilo Adaptive Real-Time Session Suite (Step 0 → W7)

> Program closeout, 2026-07-06. Written to be accurate over flattering: every
> "works" claim below was proven on the live preview estate, and every honest
> seam is recorded in
> [`docs/product-truth/session-suite-adaptive-realtime.md`](../product-truth/session-suite-adaptive-realtime.md).

## Mandate

Five session modes — TELEMEDICINE, LEARNING_LIVE, LEARNING_RECORDING,
MEETING, LIVE_EVENT — over **one LiveKit substrate**, built
**inventory-first** (rtc-gateway, live-service, PCT teleconsult, Fundo,
Khuluma, document-service all pre-existed; no new system of record, no
duplicate media stack) and with **no generic video page**: every room is a
governed, mode-templated, role-granted session attached to a canonical
transaction. Doctrine-as-data lives in
`contracts/schemas/session-templates/*.json`; interpreters (rtc-gateway token
grants, live-service provisioning, BFF/UI layouts) version with the
templates.

## Waves

| Wave | Delivered |
|---|---|
| **Step 0** | Substrate proof: two REAL browser contexts join one governed teleconsult room and mutually subscribe (`e2e/session-media-core.spec.ts`, `0988f5056`, `5c142bd56`); idempotent session provisioning (`cb7b7ce70`). |
| **W0** | Session-mode template contract + registry library (`c95d14db7`); LiveKit webhook ingestion + template-driven token grants (`3b8374401`); rtc outbox actually bridges to Kafka (`d1655d924`, `05aa4aa89`); live-service consumes `impilo.rtc.*` media truth (`b6c45f071`). |
| **W1** | Recording lifecycle with template policy gates (`c28f0e099`); LiveKit Egress in-chart + recording bucket (`a87db3ae3`); document-service adopts externally-written objects (`1adfdc983`); real replay pipeline from recording artifacts (`a6e7b1726`); estate proof `scripts/e2e/session-recording-proof.sh` (`8f88e6804`). |
| **W2 — TELEMEDICINE** | Template-driven waiting-room lobby + token refresh + media profiles (`7164bb3c2`); BFF/khuluma waiting-room orchestration + booking + on-call (`cbfdbe65b`); patient telehealth journey + provider console on web (`7fb021f74`); waiting-room/call screens on both mobile apps (`0a218fb3c`); patient-token identifier-space fix (`5f68ca1ee`). |
| **W3 — LEARNING_LIVE** | Configurable completion rules + webhook-accurate live attendance (`dd756570d`); classroom on web (`89ccf88df`) and both mobile apps (`f15e0c364`); proof `scripts/e2e/learning-live-proof.sh` (`f4381a841`). Stabilisation surfaced the biggest platform finds (below): LiveKit protocol skew, hardcoded LIVE_EVENT sessionType, `LocalDevMediaProvider` in preview (`15ac3cc02`), v1.1 header gaps (`976861908`, `0cf915726`), token stability (`e72b4c674`), webhook race (`19ef4e510`). |
| **W4 — LEARNING_RECORDING** | V029 replay adoption + watch schema (`d89c581fd`); replay adoption consumer (`5991ef5c7`); watch-progress + real WATCH_THRESHOLD (`c920c0114`); BFF playback exchange (`f03323d9e`); web CoursePlayer module (`2edc7103a`, `d3ab7657b`); citizen mobile player (`52a9f4101`); replay-ordering fixes (`5e0558663`, `db91cf870`); certificate fix (`c6e55aa04`); proof `scripts/e2e/learning-recording-proof.sh`. Wave note: `docs/product/learning-recording-w4-course-player.md`. |
| **W5 — MEETING** | Khuluma V008 meeting admissions + action items (`a814b864c`); meeting domain over live + rtc (`9d22b315e`, `457847506`); API + BFF relays (`df2e62bf2`, `a7774a06e`); web `/meet` routes + room components (`84fbf26c3`, `477303b42`); mobile MeetingScreen (`9fa6b8174`); HMAC invite secret config (`dbcc8155a`); the three dead-by-construction chain fixes + idempotency defect (below); proof `e2e/meeting-flow.spec.ts` (`0e2ce147a`). Doc: `docs/architecture/MEETING_SESSION_W5.md`. |
| **W6 — LIVE_EVENT** | V004 stage requests + backstage linkage (`2233ea071`); stage request machine + server-side role resolution (`63193913a`); backstage child room + role-enforced tokens + capacity (`ad410f939`); announcements on moderated chat (`147e1c426`); analytics with rtc media-quality aggregates (`540218aba`); flagged-off RTMP stream-out API (`e45511f5c`); role-tier live room + backstage console + landing CTAs (web); BFF relays (`7168a70ca`); proof `e2e/live-event-stage.spec.ts` (`a26e05ead`, `98ff9415d`). Close evidence: stage proof green twice + cross-wave regression (`f221232f5`). |
| **W7** | Program closeout: these documents (mode runbooks, product-truth record, notification-template runbook, this report) + coordinator code cleanup. |

## The major platform finds

Every one of these was found **only by live estate proofs — unit suites were
green throughout**.

1. **LiveKit protocol-17 skew (the big one).** LiveKit server v1.8.4 ↔
   livekit-client 2.19: the server never acked client offer IDs, so **EVERY
   room in EVERY mode** churned through 15-second NegotiationError/resume
   loops. Found via instrumented churn diagnostics (`c0cc4cb4f`); fixed by
   upgrading the server to v1.13.3 + egress v1.13.0 (`eebc7272b`), with the
   client publishing only after `RoomEvent.Connected` (`fbebafbf0`).
2. **Hardcoded sessionType.** live-service provisioned **all** rooms as
   `LIVE_EVENT` — learners in classrooms were minted subscribe-only broadcast
   tokens. Fixed with the `LiveMode.sessionMode()` mapping (`73779289e`) plus
   legacy role aliases (`cd0f1dcea`).
3. **Khuluma meeting chain dead-by-construction in three layers**: localhost
   base URLs in preview (`96e2c60c8`), live events scheduled with mode
   `VIRTUAL` instead of `PROFESSIONAL_MEETING` (`0c54eaecb`), and
   `owningService: khuluma-service` instead of the canonical `KHULUMA`
   (`297bc8437`) — any one of which made meetings silently media-less — plus
   the khuluma outbound `Idempotency-Key` defect (`c5a411ca6`).
4. **Replay pipeline ordering gap**: a recording artifact arriving before the
   room finished was never published (`5e0558663`), and `replay.published`
   carried a bare payload consumers couldn't adopt (`db91cf870`).
5. **Rule-governed completions never issued certificates** — only the legacy
   attendance path had issuance; fixed idempotently on the rules path
   (`c6e55aa04`).
6. **rtc webhook provision-commit race**: LiveKit v1.13 fires `room_started`
   inside the provisioning transaction window; fixed with a bounded retry
   (3 × 700 ms) in webhook session resolution (`19ef4e510`).

## Proof-driven verification law

The program's operating law, re-proven six times over: **a green unit suite
is not evidence a journey works.** Each wave closed only when its proof ran
green against the live estate, asserting database rows, minted-JWT grant
claims, real mp4 bytes over signed URLs, and real browser media — and each of
the finds above had survived a fully green unit/CI lane before the estate
proof caught it.

## Final estate state (at close, estate @ `f221232f5` lane)

- **All six mode proofs green** on the preview estate (2026-07-05/06):
  `session-media-core` + `telehealth-patient-flow` (TELEMEDICINE),
  `session-recording-proof.sh`, `learning-live-proof.sh` (10 checks),
  `learning-recording-proof.sh` (15 checks), `meeting-flow`,
  `live-event-stage` (run green twice).
- **Scenario A/B/C regression green** alongside (cross-wave regression in the
  W6 close evidence) — the suite did not break the clinical, billing, or
  learner journeys.

## Carry-forward list

Honest seams (full detail + verification in the
[product-truth record](../product-truth/session-suite-adaptive-realtime.md)):
SIP/PSTN dial-in; ASR transcripts; guest/unauthenticated invite identities
(tshepo guest tier); RTMP stream-out; announcement notification dispatch;
demote-is-next-mint; backstage attendance untracked (deliberate); recording
retention/lifecycle; provider-mobile course player; TLS for external browser
media (DNS + cert infra); calendar/ICS invites.

Additional engineering carry-forwards:

- **rtc-gateway `/error` dispatch permit** — the Spring error dispatch is not
  permitted in `SecurityConfig`, so exceptions on the permit-all webhook chain
  can be re-intercepted by the authenticated chain and masked as 401s; add an
  explicit error-dispatch permit.
- **Switch-Context-over-classroom UX** — the shell raises the Switch Context
  overlay on top of live rooms; the e2e holds dismiss it (`f8ba9aa5d`,
  `a2f19af51`) but the UX should not demand it mid-session.
- **`preview-sandbox-persistence.spec.ts` flake** — known flaky in the
  orchestrated lane; needs stabilisation or quarantine.
- **`scripts/e2e/khuluma-wave1-smoke.sh` is stale** — still targets the
  pre-suite khuluma Wave-1 surface (port 8390 default) and predates the
  meeting chain; retire or rewrite against the W5 endpoints.
- **Product-truth doctrine-shell allowlist exit condition** —
  `/work/telemedicine/{groups,virtual-hospitals}` are allowlisted as
  static-orientation doctrine shells (`6460bd03c`); if they become live
  registries they must gain sovereign BFF backing and leave the allowlist.
