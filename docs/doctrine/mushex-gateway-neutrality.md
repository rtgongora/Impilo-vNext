# MusheX — gateway neutrality and dual-mode operating doctrine

## Status

**Doctrine note** (Stage 1, additive; refined in Stage 2 with bidirectional traceability and the wallet-fallback principle). This document codifies an existing capability rather than introducing new behaviour. The runtime implementation already supports the dual-mode model described here through the `PaymentRailAdapter` SPI and `AdapterType` enum; explicit rail-selection policy and fallback configuration are tracked as follow-up work (see [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md), section *Next Gaps / Follow-up Work*, gaps **G-1 … G-6**).

This doctrine is the **controlling principle** for those gaps. Where a gap entry references this document, the doctrine here defines the target behaviour; the audit entry defines the current state, the surface, and the gap class.

## Core principle

MusheX is a **dual-mode health payment platform**:

1. **Gateway-neutral by design** — MusheX does not force a specific payment gateway, mobile-money operator, card acquirer, or bank rail onto any tenant, payer, facility, provider, or patient. Whatever the patient or facility already trusts and the regulator already approves should be routable through MusheX.
2. **Gateway-capable by default** — when no preferred gateway is configured, none is available, or the selected one is degraded, MusheX itself acts as the gateway. Care, billing, and payment intents must not stall because an external rail is missing.
3. **Health-focused always** — MusheX is not a generic payment switch. It carries health-sector semantics (encounter linkage, payer context, claims, exemptions, subsidies) regardless of which rail moved the money.

The strategic rule is short: **MusheX should not force a specific gateway, but it should guarantee that a gateway is always available.**

## Two operating modes

**Canonical Mode A admin surface (Stage 3.3).** The read-only platform-admin view of MusheX's Mode A custodial state — custodial wallets, remittance transfers, card profiles, and reversal records — is surfaced inside `one-ui-shell` at:

- [`ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx`](../../ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx) (`/finance/mushex-platform`).

It consumes the four GET routes on `FinanceMushexPlatformController` (`/internal/v1/finance/mushex-platform/wallets`, `/remittance-transfers`, `/card-profiles`, `/reversals`) via the thin TanStack Query wrappers in [`ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts`](../../ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts) and is reachable from the start-menu command `cmd-finance-mushex-platform` (FINANCE role) and from the `/finance/costa` "Invoice & payment handoff (MusheX)" cross-link. Write/admin operations (create wallet, credit/debit, create remittance, create card, create reversal) are intentionally not surfaced in this stage — they remain backend-only on `FinanceMushexPlatformController` until a later stage. This page is the canonical replacement for the MusheX admin functionality that previously lived only in the deprecated `ui/mushex-ops-console` sidecar; that sidecar's `DEPRECATED.md` has been updated to point here.

**Canonical mobile citizen wallet surface (Stage 3.4B).** The citizen-facing mobile wallet now talks to the canonical Mushe Wallet plane through experience-bff:

- [`apps/mobile/citizen-app/src/services/walletService.ts`](../../apps/mobile/citizen-app/src/services/walletService.ts) calls `GET /internal/v1/wallet/me` and `GET /internal/v1/wallet/me/transactions` (no `patientId` query — the owner is implied by the `x-actor-id` trust header that the mobile API client injects from the Keycloak session).
- [`apps/mobile/citizen-app/src/services/financeService.ts`](../../apps/mobile/citizen-app/src/services/financeService.ts) is re-targeted onto `/me/balance` and `/me/transactions` on the same plane; three dead `/internal/v1/mobile/citizen/finance/**` calls (every one a 404 at runtime) have been removed. `fetchPendingCharges` is retained as a stable stub that resolves to `[]` until a COSTA-backed pending-charges route is wired (no fabricated data).
- [`apps/mobile/citizen-app/src/screens/personal/WalletSection.tsx`](../../apps/mobile/citizen-app/src/screens/personal/WalletSection.tsx) and [`apps/mobile/citizen-app/src/screens/personal/FinanceSection.tsx`](../../apps/mobile/citizen-app/src/screens/personal/FinanceSection.tsx) now branch on the stable `WALLET_UPSTREAM_UNAVAILABLE` error code to show an honest "Wallet temporarily unavailable" `ErrorState` (with correlation id and Retry) instead of silently rendering `balance = 0` on upstream failure.
- The mobile API client's [`ApiError.fromResponse`](../../apps/mobile/packages/mobile-api-client/src/errors.ts) was extended in the same stage to recognise the BFF v1.2 nested error envelope (`{ error: { code, message } }`) so the stable upstream code reaches the UI instead of being collapsed to a generic `HTTP_503`; legacy flat `{ errorCode, errorMessage }` responses are still accepted for back-compat.

The legacy `WellnessServiceProxyController` whitelist for `/internal/v1/mobile/citizen/wallet/**` and the `CitizenMyLifeController` wallet endpoints in `wellness-service` were intentionally left in place so older mobile builds continue to function during rollout. Retiring those legacy routes belongs to a later stage gated on telemetry showing no client still hits them; new mobile builds resolve the wallet through the canonical plane on day one.

### Mode A — Orchestration gateway (preferred when external rails exist)

When a tenant/facility/payer has one or more configured gateways or payment rails:

- MusheX **routes** the payment intent to the appropriate external rail via a `PaymentRailAdapter` implementation.
- Externally, the patient/provider/payer interacts with the chosen rail (e.g. mobile money USSD, bank EFT, card acquirer, payer claims portal).
- Internally, MusheX still owns:
  - the `PaymentIntent` lifecycle and state machine,
  - the encounter / invoice / claim linkage,
  - reconciliation against the external rail’s settlement file,
  - audit, refund/reversal eligibility, fraud signals, and reporting.
- The external rail is responsible only for moving funds. All health-sector context is added, kept, and reported by MusheX.

This is the canonical mode for live deployments where a national mobile-money rail, sponsor-bank rail, or accredited card acquirer is already in production.

### Mode B — Direct / default gateway (used when no rail is preferred or available)

MusheX acts as the gateway itself when any of the following is true:

- No preferred gateway has been declared for the tenant, facility, payer, or patient context.
- A preferred gateway is declared but is not configured at runtime (missing credentials, missing endpoints, disabled adapter).
- A preferred gateway is configured but is **unavailable** (health-check failing, circuit-open, repeated webhook errors, rail provider outage).
- The flow is explicitly intended to settle inside the Impilo platform (e.g. wallet-internal transfers, subsidy disbursement, exemption write-offs, manual cash recorded at point of care, sandbox/test).

In direct mode, MusheX still issues a `PaymentIntent`, still walks the state machine, still emits audit and reconciliation events, and still anchors the encounter and payer context — but the rail used is internal (e.g. wallet ledger, cash receipt, sandbox adapter) rather than an external acquirer.

This guarantees that **care is never blocked because a third-party gateway is misconfigured or down.**

### When the mode flips

Mode is not a per-tenant constant. It is decided per `PaymentIntent`, based on the resolved context (tenant, facility, payer, patient, intent type, currency, amount, channel). The [`RailSelectionPolicy`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/rail/RailSelectionPolicy.java) SPI introduced in **Stage 3.5** (audit gap G-4 closed) formalises how that decision is reached and recorded. Callers express intent via three optional fields on `CreateIntentRequest` (`preferredRailAdapter`, `allowFallback`, `directGatewayAllowed`); the deterministic [`DefaultRailSelectionPolicy`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/rail/DefaultRailSelectionPolicy.java) applies the algorithm in [`docs/design/g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md) §6 and writes a structured `rail_selection` block onto the intent's metadata together with three new keys on the `INTENT_CREATED` outbox event. Production defaults are conservative: `mushex.rail-selection.defaultRail=SANDBOX`, `mushex.rail-selection.allowDirectGateway=false`. The `mushex.rail-selection.enabled` safety switch and the `impilo_simulation=true` metadata flag both force SANDBOX regardless of any caller preference.

## Mapping to the existing implementation

### `AdapterType` enum

The canonical adapter taxonomy lives in:

- [`services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/domain/enums/AdapterType.java`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/domain/enums/AdapterType.java)

| `AdapterType` | Typical mode usage |
|---------------|--------------------|
| `MOBILE_MONEY` | Mode A — external mobile-money rail (e.g. national MNO wallets) |
| `BANK_TRANSFER` | Mode A — bank EFT / payer-bank rail |
| `CARD_GATEWAY` | Mode A — accredited card acquirer |
| `SANDBOX` | Mode B — direct/internal settlement, test, or fallback when no external rail is available |

The enum is the **only** allowed surface for declaring a rail category. New rails must extend this enum; they must not be smuggled in as free-form strings.

### `PaymentRailAdapter` SPI

- [`services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/adapter/PaymentRailAdapter.java`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/adapter/PaymentRailAdapter.java)
- [`services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/adapter/AdapterRegistry.java`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/adapter/AdapterRegistry.java)

Every concrete adapter (`MobileMoneyAdapter`, `BankTransferAdapter`, `CardGatewayAdapter`, `SandboxMockAdapter`) implements the same five-operation contract:

1. `adapterType()` — declares which `AdapterType` it handles.
2. `initiatePayment(intentId, amount, currency, config)` — starts a payment.
3. `checkStatus(adapterRef, config)` — polls the rail for state.
4. `verifyWebhook(signature, payload, config)` — validates inbound callbacks.
5. `initiateRefund(adapterRef, amount, config)` — initiates a refund.

`AdapterRegistry` aggregates all `PaymentRailAdapter` beans on startup and keys them by `AdapterType`. **Gateway neutrality is what this SPI is for** — adding a new rail is an additive operation (new bean, new enum value if needed), not a rewrite.

### Where Mode B already shows up today

A direct/default-gateway shape already exists for wallet flows: `WalletController` in the Experience BFF calls `MusheWalletServiceClient` but holds an in-memory fallback path for local development when the wallet service is unreachable:

- [`services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WalletController.java`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WalletController.java)

That fallback is not yet gated by an explicit configuration flag and is not labelled as Mode B; tightening this is tracked under follow-up work in the audit (**G-5**), not in this doctrine note.

### Wallet local-fallback principle (implemented for the BFF wallet path in Stage 3.1; extended to the read endpoints in Stage 3.1.1)

Mode B legitimises the *existence* of an in-platform fallback when no external rail is available. It does **not** legitimise silently fabricating financial state in production. The doctrinal principle around the existing `WalletController` fallback is therefore:

1. **A local / in-memory wallet fallback may be useful for development and test environments.** It is acceptable for engineers to receive a usable wallet shape from the BFF when the upstream Mushe Wallet service is unreachable on a developer laptop or in a sandbox cluster.
2. **Production must never silently fabricate wallet balances or transactions.** Synthetic balances, fake transactions, fake card stubs, or fake funding sources are not Mode B; they are a doctrine violation if returned to a real user.
3. **The fallback is gated by an explicit configuration flag.** Concrete property: `impilo.wallet.allow-local-fallback` (default `false`), bound via [`BffWalletProperties`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/BffWalletProperties.java) and consumed by [`WalletController`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WalletController.java). Environment override: `IMPILO_WALLET_ALLOW_LOCAL_FALLBACK`. The flag also exists in the BFF test profile (`application-test.yml`) set to `true` so in-JVM tests / golden paths continue to function without a live Mushe Wallet.
4. **When the upstream wallet service is unavailable and fallback is disabled, the BFF returns HTTP 503 with stable error code `WALLET_UPSTREAM_UNAVAILABLE`.** Response shape: `{ "error": { "code": "WALLET_UPSTREAM_UNAVAILABLE", "message": "…" }, "meta": { "request_id": "…", "correlation_id": "…" } }`. Callers (web shell, mobile app) can distinguish "wallet service is down" from any other failure by matching the `code`.
5. **Audit trail of fallback activations.** Each fallback activation emits a `WARN` log line with the stable marker `WALLET FALLBACK ACTIVATED` and includes `operation`, `reason`, `requestId`, and `correlationId`. If this line appears in a non-development environment, the deployment is doctrine-violating and `impilo.wallet.allow-local-fallback` should be returned to `false`.

**Stage 3.1 scope (implemented):** the gate applies to the two upstream-aware code paths in `WalletController`:

- `GET /internal/v1/wallet/me` — when `MusheWalletServiceClient.getWalletByOwner` throws or returns `null`.
- `POST /internal/v1/wallet/pay` (the `MUSHE_WALLET` payment method branch) — when `getWalletByOwner` / `debitWallet` throws or returns `null`. Non-wallet methods (`CASH`, `ECOCASH`, `INNBUCKS`, etc.) are unaffected because they never touch `MusheWalletServiceClient`.

**Stage 3.1.1 scope (implemented):** the gate now also covers the three read endpoints that previously synthesised state without ever calling upstream (audit gap **G-5.1**):

- `GET /internal/v1/wallet/me/balance` — first resolves the wallet via `getWalletByOwner("PERSON", actorId)` and then calls `MusheWalletServiceClient.getBalance(walletId)`.
- `GET /internal/v1/wallet/me/transactions` — same wallet resolution, then `MusheWalletServiceClient.getTransactions(walletId, 0, 50)`. The page/size defaults are internal and not yet exposed as query parameters on the BFF endpoint (no public contract change).
- `GET /internal/v1/wallet/me/funding-sources` — same wallet resolution, then `MusheWalletServiceClient.listFundingSources(walletId)`.

Behavioural contract is identical to Stage 3.1: upstream success returns the upstream `JsonNode` unchanged inside the standard `{ data, meta }` envelope; upstream failure with the flag off returns 503 + `WALLET_UPSTREAM_UNAVAILABLE`; upstream failure with the flag on logs `WALLET FALLBACK ACTIVATED` and returns the pre-Stage-3.1.1 in-memory shape so existing dev/test consumers keep working.

The shared upstream-resolution logic lives in a single private helper (`WalletController.lookupWalletByOwner`) reused by `/me` and the three new endpoints, so Stage 3.1.1 did not duplicate the try/catch/null-check four times.

## Health-sector logic MusheX owns (regardless of mode)

Whichever rail moves the money, MusheX is responsible for the health-sector envelope around it. The non-negotiable health-sector responsibilities are:

- **Invoices** — issued by COSTA, but referenced and settled by MusheX `PaymentIntent`s. See [`docs/doctrine/costa-mushex-billing-timing.md`](./costa-mushex-billing-timing.md) for the costing/billing/settlement separation and the `BillingTimingMode`, `CostaInvoiceType`, and `PaymentIntentType` enumerations.
- **Charge sheets** — point-of-care charge capture against an encounter, regardless of when payment lands.
- **Claims** — submission, adjudication outcome, rejection, resubmission; carried as `CLAIM_SUBMISSION` intents and claim-pack handoffs.
- **Remittances** — payer-to-facility flows reconciled against submitted claims.
- **Settlement** — facility/provider/payer settlement runs, payout releases, dispute holds.
- **Reconciliation** — matching external rail statements (mobile-money settlement files, bank EFT statements, card acquirer payouts, claim remittance advice) to MusheX intents and COSTA invoices.
- **Audit** — every state transition, adapter call, webhook receipt, manual override, exemption, and waiver is auditable end-to-end.
- **Encounter linkage** — every payment intent links to the clinical encounter and patient that justified it; orphaned payments are explicitly disallowed in health doctrine.
- **Patient / provider / facility metadata** — Health ID, Provider ID, facility, workspace, shift, and tenant/pod context are propagated via the v1.2 trust headers and persisted on the intent.
- **Payer context** — coverage, payer ID, scheme, benefit, pre-authorisation reference, co-pay percentage, exemption status.
- **Refunds and reversals** — full and partial refunds, `REVERSAL`, `REFUND_CREDIT_NOTE` invoice flows, and adapter-level refund execution.
- **Subsidies and exemptions** — health-sector specific: free-care policies, ministry subsidies, sponsor pools, vulnerable-group exemptions; recorded even when the patient pays zero.
- **Reporting** — finance, public health, payer, and tenant-level reporting on what was billed, settled, exempted, claimed, and reconciled — never just "what was paid".

A generic payment switch can move money. **MusheX must move money in a way that is auditable as health-sector activity.**

## What this doctrine does *not* yet do (deferred to later stages)

Tracked in the COSTA / MusheX wiring audit's *Next Gaps / Follow-up Work* table. Each deferred item below cites its audit gap ID so doctrine ↔ audit traceability is bidirectional:

| Deferred item | Audit gap | Belongs to |
|----------------|-----------|------------|
| Canonical COSTA finance page (`/finance/costa`) in `one-ui-shell` so the existing shell links resolve to a real page. | **G-1** | Stage 3+ |
| MusheX platform admin page in `one-ui-shell` consuming `/internal/v1/finance/mushex-platform/**`. **Implemented in Stage 3.3** as a read-only Mode A admin hub at [`/finance/mushex-platform`](../../ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx); hooks live in [`ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts`](../../ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts). Write operations remain backend-only. | **G-2** | Stage 3.3 (done; write actions deferred) |
| Mobile citizen wallet re-routing off the wellness-service proxy and onto Mushe Wallet / MusheX. **Implemented in Stage 3.4B** by rewriting [`apps/mobile/citizen-app/src/services/walletService.ts`](../../apps/mobile/citizen-app/src/services/walletService.ts) (and the previously-dead [`financeService.ts`](../../apps/mobile/citizen-app/src/services/financeService.ts)) to call the canonical `/internal/v1/wallet/me`, `/me/balance`, and `/me/transactions` plane via `WalletController`. The wallet owner is now derived from the `x-actor-id` trust header (no `patientId` query parameter). The mobile API client error parser was upgraded in the same stage to recognise the BFF v1.2 nested error envelope so the stable `WALLET_UPSTREAM_UNAVAILABLE` code survives the trip to `WalletSection` / `FinanceSection`, where it triggers an honest "Wallet temporarily unavailable" `ErrorState` instead of a fabricated zero balance. **Legacy retirement deferred:** the `WellnessServiceProxyController` whitelist entry for `/internal/v1/mobile/citizen/wallet/**` and the `CitizenMyLifeController` wallet endpoints in `wellness-service` were left in place so older mobile builds continue to function during rollout; removing them belongs to a follow-up retirement stage gated on mobile telemetry. | **G-3** | Stage 3.4B (done; legacy-route retirement deferred) |
| ~~Explicit `RailSelectionPolicy` (per tenant / facility / payer / intent-type), plus request-model fields such as `preferredRailAdapter`, `allowFallback`, and `directGatewayAllowed` on `CreateIntentRequest`. An explicit `directGateway` health-check signal distinct from per-adapter health is part of this work.~~ **Closed in Stage 3.5** with a deterministic `DefaultRailSelectionPolicy` and the three optional request fields; see [`docs/design/g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md). Tenant-/facility-/payer-scoped policy and a real-time direct-gateway health-check signal are explicitly carried forward as future hooks (§15 of the design doc); they require a tenant-preferences store and a non-stub adapter implementation, both out of scope here. | **G-4** | Stage 3.5 (done; tenant-scoping & live health-check deferred) |
| Configuration-gated wallet fallback in the BFF (`WalletController`), including the `impilo.wallet.allow-local-fallback` flag, the 503 unavailable contract, and fallback-activation auditing. **Implemented in Stage 3.1** for `GET /me` and the `POST /pay` `MUSHE_WALLET` branch; **extended in Stage 3.1.1** to `GET /me/balance`, `GET /me/transactions`, and `GET /me/funding-sources`. The gate is now consistent across every wallet code path that has a real upstream call available. | **G-5**, **G-5.1** | Stage 3.1 + Stage 3.1.1 (done) |
| `DEPRECATED.md` markers on sidecar MusheX UIs (`ui/mushex-finance-console`, `ui/mushex-ops-console`, `ui/mushex-payer-portal`) — partial: added in Stage 2; canonical replacement and migration plan are recorded in those marker files. Net-new feature work in those sidecars remains disallowed by doctrine. | **G-6** | Stage 2 (partial) / Stage 3+ for retirement |
| Observability / audit / reconciliation / operations surface coherence. **Phase 8 first slice done:** new audit doc [`phase-8-observability-audit-reconciliation-ops-audit.md`](../audits/phase-8-observability-audit-reconciliation-ops-audit.md) maps the four surface families. New cross-service outbox event catalogue at [`mushex-costa-outbox-event-catalogue.md`](../audits/mushex-costa-outbox-event-catalogue.md) records every Kafka outbox event type emitted by `mushex-service` and `costing-engine-service`, the topic each lands on per the two `routeTopic(...)` switches, and the known payload keys (including the Phase 5 G-4 `rail_selection` keys on `INTENT_CREATED`). Slices 8B (convention test for dedicated topics), 8C (finance-plane ops runbook), 8D (audit aggregate-filter), 8E (triple-source reconciliation), 8F (consumer map) queued. | **G-13** | Phase 8 (Slice 8A done; 8B–8F queued) |
| Retirement-readiness ledger + telemetry-signals catalogue. **Phase 7 first slice done:** central ledger at [`docs/retirement/retirement-readiness-ledger.md`](../retirement/retirement-readiness-ledger.md) records every outstanding deprecation in the repository (`RR-01`..`RR-07`) with a stable id, canonical replacement, observable retirement criteria, current evidence, current blockers, and a four-state status taxonomy. Companion [`docs/retirement/telemetry-signals.md`](../retirement/telemetry-signals.md) names the five telemetry signals each criterion depends on. All five existing `DEPRECATED.md` files cross-link to their ledger entry. The ledger is the single canonical join point for "things flagged for retirement"; retirement itself remains a separate, explicit batch. Operational follow-ons (production dashboards, BFF inbound counters, CI guards) are queued as slices 7B–7F. | **G-12** | Phase 7 (ledger established; operational dashboards / counters / guards queued) |
| Claims / coverage / remittances / subsidies surface coherence. **Phase 6 implemented for Slices 6A–6D.** The four read-only coverage hooks from Slice 6A are now wired into `/coverage` tabs (preauth, contributions, appeals, intelligence) with honest loading/error/empty states over existing BFF routes; claims surfaces now include explicit handoff from `/finance/claims` and `/finance/claims/[id]` into `/finance/payer-claims` plus confirmation-gated submit/dispute actions on `/finance/payer-claims/[claimId]`; and a canonical read-only remittances hub is live at `/finance/remittances` over `GET /internal/v1/coverage/remittances` with route metadata/parity updated. Slice 6E (subsidies) remains deferred — no first-class backend representation and requires product/API decision. Full audit in [`docs/audits/phase-6-claims-coverage-remittance-subsidy-audit.md`](../audits/phase-6-claims-coverage-remittance-subsidy-audit.md). | **G-11** | Phase 6 (Slices 6A–6D done; 6E deferred) |
| COSTA workflow maturity — chronological view of the *estimate → charge sheet → invoice → payment* trail plus controlled workflow actions. **Phase 5 complete:** `/finance/costa/encounter/[encounterId]` merges decisions + cost events + invoice lifecycle rows + MusheX source-list intent rows + intent-filtered settlement rows. Additive reads: COSTA `GET /costa/v1/finance/lifecycle/invoices?encounter_id=...` (BFF: `GET /internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=...`), MusheX `GET /mushex/v1/payment-intents?source_type=...&source_ids=...` (BFF: `GET /internal/v1/finance/payer-ops/payment-intents?sourceType=...&sourceIds=...`), and MusheX `GET /mushex/v1/settlements?intent_ids=...` (BFF: `GET /internal/v1/finance/settlements?intentIds=...`). Controlled actions are now surfaced on `/finance/costa` using existing routes with explicit operator confirmation + required reason capture: `POST /internal/v1/finance/service-access-decisions` and `POST /internal/v1/finance/costa-intel/invoices/from-cost-estimate`. Full design in [`docs/design/phase-5-costa-encounter-timeline.md`](../design/phase-5-costa-encounter-timeline.md). | **G-10** | Phase 5 (complete) |
| MusheX platform read-to-detail — operator-visible per-record detail surfaces, **no writes**. **Phase 4 full read-only slice done:** the wallet drill-down remains in place and Phase 4 follow-on adds additive backend + BFF per-id GETs and canonical detail pages for remittance transfers (`/finance/mushex-platform/remittance/[transferId]`), card profiles (`/finance/mushex-platform/cards/[cardId]`), and reversals (`/finance/mushex-platform/reversals/[reversalId]`), all reached from new "Open by ID" forms on `/finance/mushex-platform`. The MusheX side now returns `404 PLATFORM_RECORD_NOT_FOUND` for missing/foreign-tenant ids across the four detail families. Write actions (credit/debit, card block, reversal execute, payout release, gateway config) remain deferred behind the Phase 4 safety design (role gating, step-up auth, dual control, reason capture, audit immutability, rate/amount limits, break-glass). Full design in [`docs/design/phase-4-mushex-platform-detail.md`](../design/phase-4-mushex-platform-detail.md). | **G-9** | Phase 4 (full read-to-detail done; safety-gated writes deferred) |
| ~~Attempt-time rail enforcement — at the moment a payment attempt is created, the rail selected at intent creation (`metadata.rail_selection.effective_rail`) must be the one actually used; mismatch / legacy-intent / unregistered-at-attempt cases must be recorded; idempotency must not regress.~~ **Closed in Phase 3.** New service [`PaymentAttemptService`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/PaymentAttemptService.java) creates attempts on `POST /mushex/v1/payment-intents/{id}/attempts`; new helper [`RailEnforcement`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/rail/RailEnforcement.java) honours the persisted rail and emits `ATTEMPT_INITIATED` / `ATTEMPT_FAILED_PRE_INITIATION` outbox events. A three-leg safety gate (`mushex.adapters.<rail>.enabled` × `credentials-configured` × `PaymentRailAdapter.liveCapable()`, all default to `false`) prevents `initiatePayment(...)` on real-money rails until the operator explicitly opts in **and** a real provider client is wired into the adapter. The third leg, `liveCapable()`, was added in the Phase 3 follow-on batch as defence-in-depth against a configuration mistake invoking a stub adapter; today every adapter in this repository is a stub and returns `false`. Blocked attempts are persisted with `enforcement_metadata.safety_gate=BLOCKED_PRE_LIVE` and the adapter is never called. Idempotency at the attempt level uses the new `payment_attempts.idempotency_key` column with a partial unique index. Full implementation contract in [`docs/design/phase-3-attempt-time-rail-enforcement-implementation.md`](../design/phase-3-attempt-time-rail-enforcement-implementation.md); historical audit in [`docs/design/phase-3-attempt-time-rail-enforcement.md`](../design/phase-3-attempt-time-rail-enforcement.md). | **G-8** | Phase 3 (done; UI trigger surface + reselection / controlled-retry endpoint queued separately) |
| ~~Adapter readiness — operator-visible per-rail readiness without unsafe credential handling.~~ **Closed in Phase 2** with [`AdapterReadinessService`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/service/rail/AdapterReadinessService.java), `GET /mushex/v1/platform/adapter-readiness`, a one-line BFF passthrough, and a read-only table on [`/finance/mushex-platform`](../../ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx). Readiness is deterministic, derived from `AdapterRegistry` + `MushexProperties` config, and **never reads payment-provider credentials**. Production default: zero `liveCapable` rails; `SANDBOX` is `READY_SANDBOX`. A reserved `DEGRADED` state and live health-probing are explicitly deferred until any adapter surfaces a runtime health signal. See [`docs/design/phase-2-adapter-readiness.md`](../design/phase-2-adapter-readiness.md). | **G-7** | Phase 2 (done; DEGRADED + live probing deferred) |
| UI rail-selection / rail-override surface in `one-ui-shell`. | (follows G-4) | Stage 4+ |

Those changes must not be implemented as part of this doctrine note. They are listed here only so that the binding behaviour they will eventually have is fixed in doctrine *before* any controller, DTO, or page is touched.

## Related

- [`docs/doctrine/health-os-doctrine.md`](./health-os-doctrine.md) — Health OS unifying doctrine.
- [`docs/doctrine/costa-mushex-billing-timing.md`](./costa-mushex-billing-timing.md) — Costing, billing-timing, and settlement separation.
- [`docs/architecture/experience-bff-downstream-route-map.md`](../architecture/experience-bff-downstream-route-map.md) — Where the BFF reaches MusheX.
- [`docs/architecture/experience-bff-internal-routes.md`](../architecture/experience-bff-internal-routes.md) — Generated `/internal/v1` index.
- [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) — Current state, follow-up work, and outstanding gaps.
- [`contracts/openapi/mushex.openapi.yaml`](../../contracts/openapi/mushex.openapi.yaml) — MusheX API surface.
