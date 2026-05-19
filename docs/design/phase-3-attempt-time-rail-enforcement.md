# Phase 3 — Attempt-time rail enforcement (audit + design + stop notice)

| Field | Value |
| ----- | ----- |
| Status | **Audit complete. Implementation deferred** — see §6 for the stop condition and §7 for the future-work hand-off. |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Predecessor designs | [`g4-rail-selection-policy.md`](g4-rail-selection-policy.md), [`phase-2-adapter-readiness.md`](phase-2-adapter-readiness.md) |
| Release readiness baseline | [`docs/release/mushex-costa-finance-phase-1-release-readiness.md`](../release/mushex-costa-finance-phase-1-release-readiness.md) |

## 1. Goal

Make sure that the payment rail selected at intent creation (the `effective_rail` produced by `RailSelectionPolicy` and persisted under `payment_intents.metadata.rail_selection`) is the rail actually used when a payment attempt is executed. Record any rail mismatch / rejection. Preserve idempotency. Emit audit/outbox metadata.

## 2. Current state audit

I audited every code path in `services/mushex-service` that touches `PaymentAttemptEntity`, `AdapterRegistry.getAdapter(...)`, `PaymentRailAdapter.initiatePayment(...)`, or the rail-selection metadata, and every BFF path that could trigger a payment attempt.

### 2.1 What exists today

- **`PaymentAttemptEntity`** (`domain/entity/PaymentAttemptEntity.java`): a JPA entity with `id`, `intentId`, `adapterType` (enum), `adapterRef`, `amount`, `status` (`AttemptStatus` enum), `requestedAt`, `completedAt`, `rawSummary` (JSONB). The schema already has an `adapter_type` column.
- **`PaymentAttemptRepository`** (`domain/repository/PaymentAttemptRepository.java`): a Spring Data JPA repository exposing `findByIntentId(...)` and `findByAdapterRef(...)`. No `save(...)` callers other than the webhook controller (see below).
- **`PaymentRailAdapter.initiatePayment(...)`**: implemented by all four adapters. **No production code path calls it.**
- **`AdapterRegistry`**: lookup by `AdapterType`; `has(AdapterType)` added in Stage 3.5.
- **`AdapterWebhookController`** (`api/AdapterWebhookController.java`): the **only** caller of `paymentAttemptRepository.save(...)`. It receives a provider webhook keyed by `adapterRef`, looks up an *already-existing* attempt, transitions its status, and on success calls `paymentIntentService.recordPayment(...)`. It assumes the attempt already exists in the database — it never creates one.
- **`PaymentIntentService`**: creates `PaymentIntentEntity` rows, persists `metadata.rail_selection` (Stage 3.5), emits `INTENT_CREATED` outbox events with `effectiveRail` / `preferredRail` / `railSelectionReason`. Does **not** create or initiate payment attempts.
- **BFF**: no controller method on `services/experience-bff` triggers a payment attempt. `FinanceMushexPlatformController` exposes `wallets`, `wallets/{id}/{credit,debit}`, `remittance-transfers`, `card-profiles`, `reversals`, and (Phase 2) `adapter-readiness`. None of those creates a `PaymentAttemptEntity`.
- **Outbox events**: no `PAYMENT_ATTEMPT_STARTED`, `PAYMENT_ATTEMPT_SUCCEEDED`, or `PAYMENT_ATTEMPT_FAILED` event is emitted anywhere. Searching `mushex-service` for those literal strings returns zero matches.

### 2.2 What does **not** exist today

- No endpoint of the shape `POST /mushex/v1/payment-intents/{id}/attempts`.
- No service of the shape `PaymentAttemptService.createAttempt(...)` or `executeAttempt(...)`.
- No call site of `PaymentRailAdapter.initiatePayment(...)`.
- No `PaymentAttemptEvents` constants class for outbox keys.
- No idempotency model around attempt creation (the existing `idempotencyKey` is on `payment_intents`, not on attempts).
- No test fixture creating a `PaymentAttemptEntity` programmatically — the webhook tests presumably build attempts manually via the repository, but there is no service-layer creation tested.

### 2.3 Implication

There is **no payment-attempt-creation path to enforce anything against**. "Attempt-time rail enforcement" is upstream of code that has not been built yet.

## 3. What attempt-time enforcement *would* look like

When an attempt-creation flow is built, the enforcement contract should be:

1. **Read** `paymentIntent.metadata.rail_selection.effective_rail` (already persisted by Stage 3.5).
2. **Resolve** it to an `AdapterType` (case-insensitive, defensive about absent / malformed metadata).
3. **Check** that the adapter is registered (`AdapterRegistry.has(adapterType)`).
4. **Use** that adapter for `initiatePayment(...)`. Persist `PaymentAttemptEntity.adapterType = effectiveRail`.
5. **Emit** a `PAYMENT_ATTEMPT_STARTED` outbox event with keys `intentId`, `attemptId`, `attemptedRail`, `selectedRail`, `mismatch`, `idempotencyKey`, `correlationId`. (`mismatch=true` only if a forced retry/reselection occurred — see §3.2.)
6. **Idempotency**: attempt creation must be idempotent against a caller-provided attempt-idempotency key; replays must return the existing attempt **without** re-invoking `initiatePayment`. This mirrors the Stage 3.5 idempotency-replay rule for intents.

### 3.1 Legacy intent without rail metadata

Pre-Stage-3.5 intents do not carry `metadata.rail_selection`. The enforcement helper must default to the deployment's configured default rail (`mushex.rail-selection.defaultRail`, which is `SANDBOX` out of the box) and emit `legacy_intent=true` on the outbox event so reconciliation can spot it. Production must **not** silently route a legacy intent through a real-money rail.

### 3.2 Reselection (controlled retry)

A future controlled-retry surface might re-run `RailSelectionPolicy` if the originally-selected rail is no longer registered or is degraded. That is *explicit* reselection, not silent fallback; it must:

- Carry a separate `RailReselectionPolicy` or an explicit `retryReason` argument.
- Persist the new decision back onto `metadata.rail_selection.history[]`.
- Emit a `PAYMENT_ATTEMPT_RESELECTED` outbox event.
- Preserve the original intent's idempotency key on the intent; the *attempt* gets its own idempotency key.

This sits beyond Phase 3 and is called out here only to fix the contract.

## 4. Persistence

No schema change required for the enforcement layer itself:

- `PaymentAttemptEntity.adapter_type` already exists — it is where `effective_rail` lands.
- `payment_intents.metadata.rail_selection` already exists.
- Outbox payload is JSONB and accepts additive keys per the platform event-versioning rule (`docs/registry/README.md`).

A separate `payment_attempts.idempotency_key` column **would** be required if the attempt-creation flow needs idempotency at the attempt level (distinct from the intent's idempotency key). That decision belongs to the attempt-flow design itself, not to enforcement.

## 5. Test plan (deferred)

When the attempt-creation flow is built, tests must cover:

1. Attempt uses the rail in `metadata.rail_selection.effective_rail`.
2. Legacy intent (no `metadata.rail_selection`) → safe default → marked `legacy_intent=true` on the outbox event.
3. Effective rail's adapter not registered at attempt time → controlled rejection with stable error code `RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT`.
4. Idempotency replay: same `attemptIdempotencyKey` returns the existing attempt and does **not** re-invoke `initiatePayment` or republish events.
5. Sandbox simulation lock: `metadata.impilo_simulation=true` forces `SANDBOX` regardless of `effective_rail`.
6. Concurrent attempts on the same intent are serialised by the underlying intent row (consistent with the existing intent-level locking).
7. The `PaymentAttemptEntity.adapter_type` column reflects exactly what was sent to the rail.

## 6. Stop condition — why Phase 3 is not implemented in this batch

The Phase 3 task list says:

> *3. Implement only if the attempt flow is clear and contained.*

Today the flow is **not** clear and contained. Building it requires decisions that fall under the autonomy guardrails' explicit stop list:

- *§Stop and report before implementing if the change would: (8) Require a business/product decision rather than a technical implementation decision.* — The questions "who triggers an attempt?", "from which UX?", "on a separate Pay-now action or implicitly at intent creation?", "what retry semantics?", "what error UX?" are product/UX decisions, not technical ones.
- *§Stop … (3) Add production money movement write actions.* — A real attempt-creation endpoint is, by definition, a write that intends money movement. Production-safe defaults can be built (today's adapters are stubs and would return `PENDING`), but the surface itself is the gate.
- *§Stop … (4) Change database schemas.* — Attempt-level idempotency would likely require a new column (or a new table); the right design is non-trivial.
- *§Stop … (10) Touch services outside the MusheX/COSTA/wallet/finance boundary.* — Triggering attempts from `/finance/payer-ops` or `/wallet` UX touches surfaces whose information architecture has not been redesigned for attempt triggering.

Adding a read-only "rail enforcement helper" without a caller would create dead code, which the autonomy guardrails explicitly forbid:

> *§Implementation doctrine: Do not rebuild what already exists. … Reuse existing patterns; extend existing components/hooks/services instead of creating parallel ones; only create new files where there is no suitable existing home.*

A helper with no caller has no existing home; it is by definition parallel-to-future work. So Phase 3 does **not** introduce code in this batch.

## 7. Future work hand-off

When the attempt-creation flow is designed and approved, the implementation order should be:

1. Write a *Phase 3.1 — payment-attempt-creation flow* design doc with the product-UX decisions resolved.
2. Add a `PaymentAttemptService.createAttempt(intentId, attemptIdempotencyKey, ...)` method on `mushex-service`.
3. Inside `createAttempt`, call the **new helper** `RailEnforcement.resolveEffectiveRail(intent, properties, adapterRegistry)`:
   - Parses `metadata.rail_selection.effective_rail` if present;
   - Falls back to the configured default for legacy intents (and tags the attempt accordingly);
   - Throws a `RailEnforcementException` with a stable error code if the adapter is unregistered at attempt time;
   - Logs decisions at INFO with `intentId` + `attemptId` + `selectedRail` + `attemptedRail`.
4. Persist the resolved rail to `PaymentAttemptEntity.adapterType`.
5. Emit `PAYMENT_ATTEMPT_STARTED` outbox event using a new `MushexOutboxEvents.PAYMENT_ATTEMPT_STARTED` constant (also new in that batch).
6. Add the test matrix in §5.
7. Update the audit / doctrine / downstream-route-map. Open the audit gap (G-8 or similar) and close it in the same batch.

## 8. Doctrine alignment

- *Gateway-neutral by design* — preserved; enforcement reads the existing rail-selection metadata rather than re-routing.
- *Gateway-capable by default* — preserved; legacy intents (no metadata) safely fall back to the configured default (`SANDBOX` out of the box).
- *Health-focused always* — preserved; no clinical or patient surface changes.

## 9. What changed in this batch

Nothing in the code tree. Only this document was added. The audit document (`costa-mushex-experience-layer-wiring-audit.md`) and the doctrine (`mushex-gateway-neutrality.md`) get a new row tracking Phase 3 status (audit complete, implementation deferred). The implementation budget for this batch was redirected to making the future hand-off as cheap as possible: any future agent reading this document plus the predecessors (`g4-rail-selection-policy.md`, `phase-2-adapter-readiness.md`) has the full contract.
