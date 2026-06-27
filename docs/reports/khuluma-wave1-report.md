# Khuluma — Impilo Comms Hub: Wave-1 report (§28)

Native, internet-first communication & coordination layer of the Health OS — unified
conversations, presence, calls, and the realtime gateway — under Tshepo-governed, audited trust.
Wave 1 delivers a complete, gate-passing vertical slice: two users converse in realtime, read
receipts flow, a 1:1 call rings/accepts/signals/ends over self-hosted LiveKit, presence is live,
sensitive actions are policy-checked and audited, and the whole journey is surfaced on web and mobile.

- **Branch:** `intake/khuluma-comms-hub` (off `intake/wave-b`).
- **Plan:** [`docs/roadmaps/khuluma-comms-hub-plan.md`](../roadmaps/khuluma-comms-hub-plan.md).
- **Doctrine:** Khuluma owns ONLY the missing domains; it reuses channels/notification/live/
  rtc-gateway/pct as systems-of-record and never duplicates them. Media = LiveKit (canonical,
  self-hosted); no custom WebRTC signalling.

## Commits (atomic, per slice)
| Slice | Commit | Summary |
|------|--------|---------|
| W1.1 | `aabf4ef6` / `8d8a009cb` | Scaffold + durable plan (port 8390, db impilo_khuluma, V001 outbox+idempotency). |
| W1.2 | `7de33821f` | Domain migration + entities/repos + core services + internal API. |
| W1.3 | `ed7799e12` | Realtime gateway — SSE + WebSocket over Redis pub/sub fan-out. |
| W1.4 | `2c8acb786` | Policy — khuluma.rego + tshepo policy_rules seed + BFF precheck. |
| W1.5 | `60cde0c95` | BFF — KhulumaServiceClient + web/mobile controllers + notifications delegate. |
| W1.6 | `04d50e64c` | Web Comms Hub — /work/comms + /my/comms (one-ui-shell). |
| W1.7 | `36d2b4be5` | Mobile Comms Hub (citizen-app). |
| W1.8 | _this_ | E2E smoke + A/V QA checklist + registry + compose/health + gates + report. |

## Service & migrations
- **khuluma-service** (port **8390**, db **impilo_khuluma**); SecurityConfig is production-secure
  (test-only oauth off-switch, no production auth bypass).
- **V001** — `khuluma_event_outbox` (v1.1 envelope) + `idempotency_keys`.
- **V002** — `khuluma_conversations`, `_conversation_participants`, `_messages`,
  `_message_receipts`, `_conversation_links`, `_presence`, `_calls`, `_call_participants`,
  `_call_events` (+ inbox / participants / unread / presence / calls-by-participant / linked-object indexes).

## APIs
khuluma-service internal API `/internal/v1/khuluma/**`: conversations (create / inbox / get /
participants / links), messages (list / send / mark-read), unread-count, presence (update / get),
calls (start / get / accept / decline / end / signals) + v1.1 probe (`/internal/v1/health`,
`/internal/v1/test-command`).

## BFF endpoints
`experience-bff`:
- `/internal/v1/khuluma/**` — summary, inbox/conversations(+create), conversation detail,
  participants, links (clinical-gated), messages (list/send/read), unread-count (composite with
  notifications), notifications (delegated to notification-service), presence, calls (full lifecycle).
- `/internal/v1/mobile/khuluma/**` — focused mobile subset.
- `KhulumaServiceClient` is a transparent proxy (preserves downstream 403/404/201); the shared
  RestTemplate interceptor forwards trust headers + `Idempotency-Key`.

## Web / mobile routes
- Web (one-ui-shell): `/work/comms` (provider) + `/my/comms` (citizen); `CommsHub` + `CommsCallModal`
  (reuses `LiveKitConsultRoom`); hooks `useComms` + optional `useKhulumaRealtime`.
- Mobile (citizen-app): "Comms" tab → `CommsHubScreen` (reuses `LiveKitMobileConsultRoom`);
  `khulumaCommsService`.

## Realtime / media infra
- One subscription/dispatch core (`RealtimeHub`) drives **both** SSE
  (`/internal/v1/khuluma/stream/sse`) and **WebSocket** (`/internal/v1/khuluma/stream/ws`).
- `RedisRealtimeDispatcher` (@Primary) delivers locally + best-effort Redis pub/sub fan-out for
  multi-instance, with origin-stamping (no double delivery). Gated by
  `impilo.khuluma.realtime.redis-enabled` (single instance works with no Redis).
- Channel access is participant-checked; tenant-isolated at dispatch.
- Media stays in rtc-gateway + LiveKit; `RtcGatewayClient` is best-effort — call lifecycle proceeds
  even when media is unconfigured/consent-gated (honest "media unavailable").

## Policy & audit
- `infra/opa/impilo/khuluma.rego` (default deny; clinical content never over insecure transport;
  patient-link requires a clinical role) + `tshepo-authz` `V017__khuluma_policy_rules.sql` seed
  (resource types khuluma-conversation / khuluma-call / khuluma-patient-link).
- BFF `KhulumaAccessPolicyService` defense-in-depth pre-check.
- Audit/outbox events on every mutation: `impilo.khuluma.conversation.created|linked|participant-*`,
  `…message.sent|read`, `…presence.changed`, `…call.started|accepted|declined|ended` (relayed by
  the shared ops-instrumentation outbox).

## Tests & results
- **khuluma-service** `mvn -o test`: **15/15 green** — `KhulumaServicesTest` (5), `KhulumaApiMockMvcTest`
  (2), `RealtimeHubTest` (3), `RealtimeFanoutTest` (2), `RealtimeGatewayTest` (2, REAL WS+SSE
  round-trip), `KhulumaWave1E2ETest` (1, full journey + audit).
- **experience-bff** `mvn -o test -Dtest=Khuluma*`: **10/10 green** — `KhulumaAccessPolicyServiceTest`
  (4), `KhulumaBffControllerTest` (6).
- **one-ui-shell** `vitest` + `tsc --noEmit`: **CommsHub.test.tsx 4/4**, typecheck clean.
- **citizen-app** `vitest` + `tsc --noEmit`: **6/6** (`CommsService` 5, `CommsHubScreen` 1), typecheck clean.

## E2E result
`KhulumaWave1E2ETest` (automated, MockMvc) drives the §E2E journey end-to-end and **passes**:
A↔B converse → unread → read receipt → membership deny (403) → call ring → accept → signal → end →
audit-trail assertion (all expected outbox event types present). A runnable cross-stack smoke is at
[`scripts/e2e/khuluma-wave1-smoke.sh`](../../scripts/e2e/khuluma-wave1-smoke.sh).

## Manual QA checklist
[`docs/qa/khuluma-wave1-av-qa-checklist.md`](../qa/khuluma-wave1-av-qa-checklist.md) — live audio/video
media verification (not CI-automatable) to run once LiveKit is configured.

## Gates (all green)
- `check-route-inventory.sh` — PASS.
- `check-frontend-mocks-and-stubs.sh` — PASS (comms pages clean; only a pre-existing unrelated legacy warning).
- `check-product-truth.sh` — PASS (4 gaps ≤ baseline 6; 0 blockers; 92 services incl. khuluma).
- `check-backend-frontend-parity.sh` — PASS (0 blocking, 0 advisory).

## Preview readiness
- Registered in `docs/registry/services-registry.yaml` + `system-of-record-map.md`.
- `impilo_khuluma` added to `scripts/seed/01-init-databases.sql`.
- `docker-compose.runtime.yml`: `khuluma-service` (build + Dockerfile, port 8390, postgres/redis/kafka
  depends_on + actuator healthcheck); `experience-bff` wired with `KHULUMA_BASE_URL`.

## Known limitations / deferred
- **Meetings/teleconsult** (W2/W3): meeting-invite step in §E2E is deferred to W2 (reuse live-service);
  this wave covers 1:1 calls.
- **Escalation/SLA + presence depth** (W4/W7): schema reserved; logic later.
- **External adapters** (W6): SMS/WhatsApp/email/USSD/IVR remain honest configured/not-configured
  adapters via notification-service providers — not required for core, not built this wave.
- **Browser-direct realtime**: the gateway handshake is header-gated (companion filter) — browser
  EventSource/WebSocket connects via the BFF/Envoy header-injecting proxy; direct-with-query-params
  is supported in code but the production path is the proxy.
- **Mobile realtime push**: auto incoming-call trigger is a documented seam for W7; the sheet +
  accept/decline + media flow are wired.
- **Helm**: no per-service Helm charts exist in-repo; deployment is compose-based for now.

## Next wave
W2 — meetings/live (reuse live-service): meeting create/invite/join, conversation↔meeting link,
calendar surface; extend the §E2E to the meeting-invite step.
