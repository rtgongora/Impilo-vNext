# Phase 3 — Attempt-time rail enforcement (implementation design)

| Field | Value |
| ----- | ----- |
| Status | **Implemented in full.** Supersedes the audit-only [`phase-3-attempt-time-rail-enforcement.md`](phase-3-attempt-time-rail-enforcement.md) for the implementation contract. |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) (gap G-8) |
| Predecessors | [`g4-rail-selection-policy.md`](g4-rail-selection-policy.md), [`phase-2-adapter-readiness.md`](phase-2-adapter-readiness.md), [`phase-3-attempt-time-rail-enforcement.md`](phase-3-attempt-time-rail-enforcement.md) |
| Outbox catalogue | [`mushex-costa-outbox-event-catalogue.md`](../audits/mushex-costa-outbox-event-catalogue.md) |

## 1. Goal

Build the payment-attempt creation flow that the audit doc identified as missing, and enforce — at the moment an attempt is created — that the rail actually used is the one persisted by `RailSelectionPolicy` at intent creation (`metadata.rail_selection.effective_rail`). Preserve idempotency at the attempt level. Emit a dedicated outbox event for every attempt. Add a *safety gate* that never reaches a real-money adapter unless the operator has explicitly enabled both `mushex.adapters.<rail>.enabled` **and** `mushex.adapters.<rail>.credentials-configured`.

## 2. Scope decisions (these supersede the audit's "deferred" stop conditions)

The autonomy stop conditions in the audit doc were:

1. *Product/UX decisions* — who triggers an attempt? **Resolved:** The endpoint is a backend-only `POST /mushex/v1/payment-intents/{intentId}/attempts` proxied through the BFF under the existing `payer-ops` namespace. No new UI surface is created in this batch — the existing `/finance/payer-ops` UX continues to be the operator entry point. Retry semantics: the caller passes an optional `idempotencyKey`; with one, replays return the existing attempt; without one, each call creates a new attempt. (The doctrine on "controlled retries" in `phase-3-attempt-time-rail-enforcement.md §3.2` is preserved.)
2. *New money-movement writes* — **Resolved by the safety gate** in §6. No real adapter is called unless the operator has explicitly opted in per rail. SANDBOX is always safe. Real-money rails default-off.
3. *Schema changes* — **Resolved.** Two additive columns on `mushex_payment_attempts`: `idempotency_key VARCHAR(120) NULL` (with a partial unique index `(intent_id, idempotency_key) WHERE idempotency_key IS NOT NULL`) and `enforcement_metadata JSONB NULL`. Both are nullable; existing rows continue to load.
4. *Touching surfaces outside MusheX/COSTA/wallet/finance* — **Resolved:** the change only touches `services/mushex-service` and a single BFF passthrough method on `services/experience-bff/PayerOpsController`. No UI surface change.

## 3. Contract

### 3.1 Endpoint

```
POST /mushex/v1/payment-intents/{intentId}/attempts
Content-Type: application/json
X-Idempotency-Key: <optional> (alternative to body field)

{
  "idempotencyKey": "optional",
  "reason": "optional human-readable label"
}
```

Returns `201 Created` with body `ApiResponse<PaymentAttemptEntity>`.

The `idempotencyKey` may be passed either in the JSON body or via the standard `X-Idempotency-Key` HTTP header; the body field takes precedence.

### 3.2 BFF passthrough

```
POST /internal/v1/finance/payer-ops/payment-intents/{intentId}/attempts
```

Lives on the existing `PayerOpsController` next to `POST /payment-intents/{intentId}/cancel`. Carries through `X-Trust-*` headers via `MushexServiceClient` (identical to the existing `cancel` proxy).

### 3.3 Behaviour

1. Look up the intent. If not found → `404 INTENT_NOT_FOUND`.
2. If `idempotencyKey` is present:
   - `findByIntentIdAndIdempotencyKey(intentId, key)` → if present, return the existing attempt. **No** new adapter call. **No** new outbox event.
3. Compute `RailEnforcementOutcome` from the intent's `metadata.rail_selection.effective_rail` (see §4 / `RailEnforcement` helper).
4. Apply the **safety gate** (§6). If blocked, persist the attempt with `status=INITIATED` and `enforcement_metadata.safety_gate=BLOCKED_PRE_LIVE`; **do not** call `adapter.initiatePayment`; emit `ATTEMPT_INITIATED` outbox event.
5. Otherwise, call `adapter.initiatePayment(intentId, amount, currency, Map.of())`.
6. Map the adapter response status to `AttemptStatus`:
   - `SUCCESS` → `SUCCEEDED` and `paymentIntentService.recordPayment(intentId, amount)`.
   - `PENDING` → `PROCESSING` (await webhook).
   - `FAILED` → `FAILED`.
7. Persist the attempt with the response's `adapterRef` and full `enforcement_metadata`.
8. Emit `ATTEMPT_INITIATED` outbox event with rail enforcement details.

### 3.4 Errors

| HTTP | Error code | Cause |
| ---- | ---------- | ----- |
| 404 | `INTENT_NOT_FOUND` | `intentId` does not exist. |
| 422 | `RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT` | The intent's `effective_rail` adapter is not registered (e.g. removed between intent creation and attempt). |
| 422 | `INTENT_NOT_PAYABLE` | The intent is in a terminal status (`CANCELLED`, `FAILED`, `REFUNDED`) and cannot be paid. (Returned before any adapter call.) |
| 500 | `ATTEMPT_PERSIST_FAILED` | Database failure during attempt creation; logged with correlation id. |

The flow never returns `200` for a real-money adapter call that was blocked by the safety gate — instead, the attempt is recorded as `INITIATED` and the response payload carries `enforcement_metadata.safety_gate=BLOCKED_PRE_LIVE`. This makes the gate visible to callers and to audit logs while not pretending money has moved.

## 4. RailEnforcement helper

A new, pure helper in `service.rail` package:

```java
public final class RailEnforcement {
    public RailEnforcementOutcome resolve(PaymentIntentEntity intent,
                                          MushexProperties.RailSelection config,
                                          AdapterRegistry registry);
}
```

Algorithm:

1. Parse the intent's `metadata`. If parse fails (non-JSON or empty), treat as a **legacy intent**.
2. If `metadata.impilo_simulation == true`, force `effectiveRail = SANDBOX` with `reason=SIMULATION_LOCK`.
3. Else read `metadata.rail_selection.effective_rail`:
   - Present and resolves to a registered `AdapterType` → `reason=EXPLICIT_METADATA`.
   - Present but unknown enum value → throw `RailEnforcementException("RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT")`.
   - Present, valid `AdapterType`, but **not** registered in the registry → throw the same exception. (This is the rail-deregistered-between-intent-and-attempt case.)
   - Missing → fall back to `config.defaultRail` with `reason=LEGACY_FALLBACK`.

`RailEnforcementOutcome` carries: `effectiveRail`, `preferredRail` (from metadata, may be null), `reason`, `legacyIntent` (true when reason is `LEGACY_FALLBACK`), and `selectionVersion` (passed through from metadata).

`RailEnforcementReason` enum: `EXPLICIT_METADATA`, `LEGACY_FALLBACK`, `SIMULATION_LOCK`.

## 5. Idempotency model

Attempt-level idempotency is per-attempt. The schema gets a new nullable `idempotency_key` with a partial unique index on `(intent_id, idempotency_key) WHERE idempotency_key IS NOT NULL`.

| Caller passes | Behaviour |
| ------------- | --------- |
| no key | a new attempt is always created |
| key, no prior attempt | new attempt with that key |
| key, prior attempt with same key | the existing attempt is returned, **no** adapter call, **no** outbox event |

Two replays with the same key are guaranteed to yield exactly one external rail call — the same property the intent layer offers via its own `idempotency_key`. The partial unique index enforces this at the database level even if the application's check race-loses.

## 6. Safety gate

```
if effective_rail == SANDBOX:
    safe = true
elif effective_rail in {MOBILE_MONEY, BANK_TRANSFER, CARD_GATEWAY}:
    real_rail_cfg = mushexProperties.adapters.<rail>   # RealRail
    safe = real_rail_cfg.enabled
        && real_rail_cfg.credentialsConfigured
        && adapter.liveCapable()      # third leg, added in the follow-on batch
else:
    safe = false  # should be impossible — defended by RailEnforcement
```

The third leg, `adapter.liveCapable()`, is a hard runtime guard introduced in the
follow-on batch. The `PaymentRailAdapter` interface defaults `liveCapable()` to
`false`; every adapter implementation in this repository today is a stub that logs and
returns `PENDING` with a synthetic reference, so all four current adapters return
`false`. Even an operator who flips both configuration flags on cannot trigger a real
provider call until a real provider client is wired into the corresponding adapter and
that adapter's `liveCapable()` is overridden to return `true`. The two leg model
(config flags only) is preserved as the operator-visible primary gate; the third leg is
the silent defence in depth that prevents a configuration mistake from invoking a stub
in the same code path that a future real client will sit in.

When the adapter is registered but its `liveCapable()` returns `false`, the safety gate
records `safety_gate=BLOCKED_NOT_LIVE_CAPABLE` (distinct from
`safety_gate=BLOCKED_PRE_LIVE` used when the config flags are off). The attempt is
still persisted with `status=INITIATED`, no adapter call is made, and an
`ATTEMPT_INITIATED` outbox event is emitted carrying the
`BLOCKED_NOT_LIVE_CAPABLE` marker.

When `safe == false`:

- The attempt is persisted with `status = INITIATED` and `adapterRef = null`.
- `enforcement_metadata` records:
  ```json
  {
    "effective_rail": "MOBILE_MONEY",
    "preferred_rail": "MOBILE_MONEY",
    "reason": "EXPLICIT_METADATA",
    "legacy_intent": false,
    "safety_gate": "BLOCKED_PRE_LIVE",
    "safety_gate_detail": "mushex.adapters.mobile-money.{enabled,credentials-configured} must both be true",
    "enforcement_version": "1"
  }
  ```
- An `ATTEMPT_INITIATED` outbox event is published with the same `safety_gate=BLOCKED_PRE_LIVE` marker so consumers can spot it.
- The response payload includes `enforcement_metadata.safety_gate=BLOCKED_PRE_LIVE`. The HTTP status is still `201 Created` — the attempt did get created in the database — but the caller can read the safety gate from the body.

This gate is the principal reason the autonomy stop condition on "production money movement" is satisfied. Real adapters are guaranteed not to be called unless an operator has explicitly turned both `enabled` and `credentials-configured` on for that rail. Defaults are off out of the box for all three real-money rails.

## 7. Outbox events

Two new event types, routed to dedicated topics in `OutboxPublisher.routeTopic(...)`:

| Event | Topic |
| ----- | ----- |
| `ATTEMPT_INITIATED` | `mushex.payment.attempt.initiated` |
| `ATTEMPT_FAILED_PRE_INITIATION` | `mushex.payment.attempt.failed` |

### Design choice — emitting `ATTEMPT_INITIATED` even on blocked attempts

When the safety gate blocks the call (`BLOCKED_PRE_LIVE` or `BLOCKED_NOT_LIVE_CAPABLE`)
the service still emits `ATTEMPT_INITIATED` with the corresponding `safetyGate` marker
on the payload. This is *intentional* and is the way operators learn that someone tried
to initiate a real-money attempt on a gated rail. The alternative — silently swallowing
the gated attempt at the outbox layer — would create a blind spot in audit
reconciliation. Topic consumers that need a "real money moved" stream filter on
`safetyGate == "OK"`; topic consumers that need an "operator attempted but was gated"
stream filter on `safetyGate != "OK"`.

### `ATTEMPT_RESELECTED`

Added in the follow-on batch. Topic: `mushex.payment.attempt.reselected`. Payload keys
include `intentId`, `previousRail`, `effectiveRail`, `preferredRail`, `reason`, and
optional `retryReason`. Emitted whenever an operator (via
`POST /mushex/v1/payment-intents/{id}/attempts/reselect`) re-runs rail selection on an
existing intent. No money movement is implied — the corresponding `metadata.rail_selection`
on the intent is updated and the previous selection is preserved on
`metadata.rail_selection.history[]`.

`ATTEMPT_INITIATED` payload keys:

```json
{
  "intentId": "01F…",
  "attemptId": "01G…",
  "adapterType": "SANDBOX",
  "preferredRail": "MOBILE_MONEY" | null,
  "effectiveRail": "SANDBOX",
  "enforcementReason": "SIMULATION_LOCK" | "EXPLICIT_METADATA" | "LEGACY_FALLBACK",
  "safetyGate": "OK" | "BLOCKED_PRE_LIVE",
  "amount": "12.34",
  "currency": "USD",
  "status": "PROCESSING",
  "legacyIntent": false,
  "idempotencyKey": "optional",
  "adapterRef": "SANDBOX-01H…" | null
}
```

`ATTEMPT_FAILED_PRE_INITIATION` is emitted when `RailEnforcement.resolve(...)` throws (`RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT`) or when the intent is in a terminal status (`INTENT_NOT_PAYABLE`). It carries `errorCode`, `errorMessage`, plus the intent identifiers and any partial enforcement metadata.

## 8. Concurrency

Existing intent-level locking (Hibernate's optimistic lock on `PaymentIntentEntity.version`) is sufficient because the attempt-creation flow only **reads** the intent (for amount, currency, metadata) and writes the attempt; it does **not** mutate the intent except via `PaymentIntentService.recordPayment(...)` on synchronous success, which already takes the standard intent lock.

Two concurrent attempts on the same intent with the same idempotency key: the partial unique index will reject the second insert; the service catches `DataIntegrityViolationException`, re-reads, and returns the existing attempt.

## 9. Tests (this batch ships all of these)

| Test | What it asserts |
| ---- | ---------------- |
| `RailEnforcementTest` | (a) metadata-driven `EXPLICIT_METADATA`; (b) missing metadata → `LEGACY_FALLBACK` to config default; (c) `impilo_simulation=true` → `SIMULATION_LOCK` regardless of effective_rail; (d) unknown enum value → throws `RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT`; (e) valid enum value but adapter unregistered → throws `RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT`. |
| `PaymentAttemptServiceTest` | (a) SANDBOX path — adapter called, `SUCCESS` → `SUCCEEDED`, `recordPayment` invoked, outbox `ATTEMPT_INITIATED` published; (b) MOBILE_MONEY path with safety gate off → no adapter call, `BLOCKED_PRE_LIVE`, attempt persisted, outbox emitted; (c) MOBILE_MONEY with both flags on → adapter called, `PENDING` → `PROCESSING`; (d) idempotency replay → existing attempt returned, no second adapter call, no second outbox event; (e) intent not found → `INTENT_NOT_FOUND` `ResourceNotFoundException`; (f) intent in terminal status → `INTENT_NOT_PAYABLE` + `ATTEMPT_FAILED_PRE_INITIATION` outbox event; (g) legacy intent (no rail metadata) → SANDBOX by default; (h) `impilo_simulation=true` on a real-rail intent → SANDBOX adapter is used (simulation lock). |
| `PaymentAttemptControllerTest` | MockMvc, asserts 201 happy path, 404 not-found, 422 not-payable, idempotency-via-header, idempotency-via-body, both forms collapsed by the service. |

## 10. Backward compatibility

- The intent-creation contract is unchanged.
- The webhook controller is unchanged. The new flow records attempts; webhooks continue to flip status as they do today.
- `PaymentAttemptEntity` gains two optional fields. Existing fields and accessors are unchanged.
- `PaymentAttemptRepository` gets one new finder. Existing finders are unchanged.
- The new `idempotency_key` column is nullable. Existing attempts loaded by the test fixtures and the webhook flow have `null` here.
- `OutboxPublisher.routeTopic(...)` gains two new switch arms; the default arm is unchanged.
- `PayerOpsController` gains one new mapping; existing mappings are unchanged.
- `experience-bff.openapi.yaml` and `mushex.openapi.yaml` gain new paths; existing schemas are unchanged.

## 11. Future work (intentionally out of scope here)

Phase 3 follow-on batch — landed in the same authoring stream:

- **UI trigger surface** for `/finance/payer-ops`. A minimal sandbox-safe trigger surface
  (read-only intent panel + confirmation-dialog "Initiate attempt" button + reselect
  affordance + attempts history) lives on the existing `/finance/payer-ops` page. The
  surface relies entirely on the backend safety gate for live-money protection; no
  step-up authentication / role escalation / threshold gating exists in the UI yet.
- **Reselection endpoint**. `POST /mushex/v1/payment-intents/{id}/attempts/reselect`
  re-runs `RailSelectionPolicy` against the persisted intent, writes the new selection
  to `metadata.rail_selection`, preserves the previous decision on
  `metadata.rail_selection.history[]`, and emits the `ATTEMPT_RESELECTED` outbox
  event. The endpoint never moves money — no adapter is called.
- **Attempt-listing endpoint**. `GET /mushex/v1/payment-intents/{id}/attempts` (+ BFF
  passthrough) is a chronological read endpoint backed by
  `PaymentAttemptRepository.findByIntentIdOrderByRequestedAtAsc`.
- **Adapter `liveCapable()` runtime guard**. A third leg of the safety gate refuses to
  call any adapter whose `liveCapable()` returns `false`. See §6 for full semantics.
- **`IntentNotFoundException` 404 mapping retrofit**. The existing
  `GET /mushex/v1/payment-intents/{id}` now returns `404 INTENT_NOT_FOUND` for unknown
  ids (previously this surfaced as `500`). The new `IntentNotFoundException` is mapped
  by a class-level `@ExceptionHandler` so every endpoint on the controller benefits.

Still out of scope (queued as separate stop conditions):

- A step-up authentication path on the UI trigger surface (`X-Step-Up-Assurance`
  header, confirmation modal, threshold-gated rails). Today the UI relies entirely on
  the backend safety gate and the `liveCapable()` runtime guard.
- Real provider integrations for `MobileMoneyAdapter`, `BankTransferAdapter`,
  `CardGatewayAdapter`. Each integration lands behind the same safety gate and the
  `liveCapable()` override flips to `true` in the same change-set that introduces the
  provider client.

## 12. Doctrine alignment

- *Gateway-neutral by design* — preserved; enforcement reads the existing rail-selection metadata rather than re-routing.
- *Gateway-capable by default* — preserved; SANDBOX is the default, legacy intents fall back to SANDBOX, and real rails are gated by both `enabled` and `credentials-configured`.
- *Health-focused always* — preserved; no clinical surface touched.
- *No silent fake data* — preserved; safety-blocked attempts are persisted and surfaced honestly with `safety_gate=BLOCKED_PRE_LIVE` on both the entity and the outbox event.
