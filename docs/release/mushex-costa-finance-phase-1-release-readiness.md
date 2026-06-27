# MusheX / COSTA / wallet / finance — Phase 1 release-readiness report

| Field | Value |
| ----- | ----- |
| Status | Phase 1 (release-readiness) complete |
| Roadmap | Phases 1–8 (release-readiness → real rail readiness → attempt-time enforcement → platform ops → COSTA workflow → claims/medical-aid/remittances → retirement readiness → observability) |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit of record | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Design baseline | [`docs/design/g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md) |

This document consolidates the position of all completed stages so far and gives operators / reviewers / future agents a single place to verify the release-readiness of the MusheX / COSTA / wallet / finance surface. **No new runtime behaviour is introduced in Phase 1.** One small documentation drift was found and corrected (component catalog sidecar rows; see §11).

## 1. Stages completed (chronological)

| Stage | Audit gap | Outcome |
| ----- | --------- | ------- |
| Stage 1 | — | Created [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) and refreshed the internal-routes index. No runtime change. |
| Stage 2 | G-6 (marker), G-5 (principle) | Added `DEPRECATED.md` markers to `ui/mushex-finance-console`, `ui/mushex-ops-console`, `ui/mushex-payer-portal`. Documented the wallet local-fallback principle. Refreshed service catalogue cross-references. No runtime change. |
| Stage 3.1 | G-5 | `WalletController.GET /me` and `POST /pay` (MUSHE_WALLET branch) became upstream-first with stable `WALLET_UPSTREAM_UNAVAILABLE` 503 + `WARN` "WALLET FALLBACK ACTIVATED" gated on `impilo.wallet.allow-local-fallback`. |
| Stage 3.1.1 | G-5.1 | Same gate extended to `GET /me/balance`, `GET /me/transactions`, `GET /me/funding-sources`. Wallet contract is now uniform. |
| Stage 3.2 | G-1 | Canonical `/finance/costa` page added to `ui/one-ui-shell` (read-only COSTA hub). `cmd-finance-costa` start-menu command now resolves. |
| Stage 3.3 | G-2 | Canonical `/finance/mushex-platform` page added to `ui/one-ui-shell` (read-only Mode A admin hub; no write actions). |
| Stage 3.4A | — | Audit-only stage: produced the change plan for the mobile citizen wallet re-route. No code change. |
| Stage 3.4B | G-3 | Mobile citizen wallet re-routed to canonical `/internal/v1/wallet/**`. Three dead `/internal/v1/mobile/citizen/finance/**` calls retired. `ApiError.fromResponse` upgraded to preserve `WALLET_UPSTREAM_UNAVAILABLE`. Legacy `wellness-service` wallet routes intentionally retained for older mobile builds. |
| Stage 3.4C | — | Housekeeping: registered `/finance/costa` and `/finance/mushex-platform` in `routes.ts`, the `EXPECTED_ROUTE_COUNT`, and the `route-parity-check.mjs` script. Cross-references added to the three sidecar `DEPRECATED.md` files. |
| Stage 3.5 | G-4 | `RailSelectionPolicy` SPI, `DefaultRailSelectionPolicy`, three optional `CreateIntentRequest` fields (`preferredRailAdapter`, `allowFallback`, `directGatewayAllowed`), additive `INTENT_CREATED` outbox keys, additive OpenAPI changes, conservative production defaults. No database schema change. |

## 2. Gaps closed

| Gap | Status |
| --- | ------ |
| G-1 | Implemented (Stage 3.2) |
| G-2 | Implemented (Stage 3.3); write actions deferred by design |
| G-3 | Implemented (Stage 3.4B); legacy `wellness-service` wallet routes intentionally retained |
| G-4 | Implemented (Stage 3.5); tenant-scoped rail policy and live adapter health-checks remain as documented future hooks |
| G-5 | Implemented (Stage 3.1) |
| G-5.1 | Implemented (Stage 3.1.1) |
| G-6 | Markers in place (Stage 2); folder retirement intentionally deferred |

## 3. Files changed by area

The current uncommitted change-set (see `git status`) covers six areas only:

| Area | What changed |
| ---- | ------------ |
| `services/mushex-service` | New `service/rail/*` package; extended `PaymentIntentService` / `PaymentIntentController` / `CreateIntentRequest` / `MushexProperties` / `AdapterRegistry`; new `RailSelectionReason` enum; updated tests. |
| `services/experience-bff` | `WalletController` upstream-first gating (Stage 3.1/3.1.1); new `BffWalletProperties`; new `EhrPatientSummaryController` and `MvumoServiceProxyController` (out-of-scope for this report but documented elsewhere); new `ServiceAccessDecisionFinanceBffController`; tests updated. |
| `apps/mobile/citizen-app` | `walletService.ts` and `financeService.ts` rewritten onto canonical `/internal/v1/wallet/**`; `WalletSection` and `FinanceSection` UI now branch on `WALLET_UPSTREAM_UNAVAILABLE`. |
| `apps/mobile/packages/mobile-api-client` | `ApiError.fromResponse` now parses the BFF v1.2 nested error envelope. |
| `ui/one-ui-shell` | New canonical pages: `/finance/costa` (Stage 3.2) and `/finance/mushex-platform` (Stage 3.3); routes registry housekeeping (Stage 3.4C); shared hooks under `src/hooks/queries/`. |
| `contracts/openapi/mushex.openapi.yaml` | Three optional fields appended to `CreateIntentRequest`; new `RailSelection` component schema. **No `required` list expansion.** |
| `docs/**` | New doctrine (`mushex-gateway-neutrality.md`), new design (`g4-rail-selection-policy.md`), updated audit (`costa-mushex-experience-layer-wiring-audit.md`), updated downstream-route-map, component-catalog drift fix (this stage). |
| `ui/mushex-*-console/DEPRECATED.md` | Three new `DEPRECATED.md` markers; sidecar **source** untouched. |

Areas explicitly **not** touched: clinical, registry, identity, learning, dispatch, support, imaging, pharmacy, inventory, wellness (except confirming legacy wallet routes still exist).

## 4. Tests run

| Suite | Result |
| ----- | ------ |
| `mvn test` in `services/mushex-service` (full suite) | **138 / 138 pass** (last run before this report: 2026-05-13 02:26 +02:00). Includes 13 `DefaultRailSelectionPolicyTest`, 33 `PaymentIntentServiceTest` (8 are new G-4 cases), 8 `PaymentIntentIntegrationTest` (3 are new G-4 MockMvc cases), 11 `RemittanceServiceTest`, 15 `WebhookSecurityTest`, plus ClaimServiceTest / LedgerServiceTest / FraudDetectionServiceTest / OutboxPublisherTest / MushexGoldenContractIT. |
| `services/experience-bff` wallet tests (Stage 3.1 / 3.1.1) | Last full run during Stage 3.1.1: **19 `WalletControllerTest` tests pass.** |
| `apps/mobile/citizen-app` walletService/financeService tests | Last full run during Stage 3.4B: all new service-level and UI smoke tests pass. |
| `ui/one-ui-shell` `routes.test.ts`, `route-parity-check.mjs`, `page.test.tsx` for COSTA/mushex-platform | Last full runs during Stages 3.2 / 3.3 / 3.4C: pass. |

No tests are currently skipped under the change-set's authored test files. Pre-existing skips in other suites are out-of-scope.

## 5. OpenAPI changes

| Contract | Change | Type |
| -------- | ------ | ---- |
| `contracts/openapi/mushex.openapi.yaml` → `CreateIntentRequest` | Added 3 optional properties (`preferredRailAdapter` enum, `allowFallback` boolean, `directGatewayAllowed` boolean). | **Additive.** `required:` list unchanged. |
| `contracts/openapi/mushex.openapi.yaml` → new `RailSelection` component schema | Documents the metadata block emitted onto `payment_intents.metadata.rail_selection` and the additive `INTENT_CREATED` outbox keys. | **Additive.** No reference from any operation; reference-only schema. |
| `contracts/openapi/experience-bff.openapi.yaml` | Unrelated incremental additions tracked in the audit. | Additive. |
| `contracts/openapi/costa-billing-extensions.yaml` *(new)* | Additive — describes COSTA billing extensions surfaced by `costing-engine-service` and the BFF; out-of-scope for this report. | Additive (new file). |

All MusheX/COSTA OpenAPI changes are additive and backward-compatible. No `required` lists expanded. No enums shrunk. No properties renamed or removed.

## 6. Configuration changes

| Property | Default | Where bound | Notes |
| -------- | ------- | ----------- | ----- |
| `impilo.wallet.allow-local-fallback` | `false` | [`BffWalletProperties`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/BffWalletProperties.java) (`services/experience-bff`) | Production-safe default. `application-test.yml` sets `true` for local dev/tests only. |
| `mushex.rail-selection.enabled` | `true` | [`MushexProperties.RailSelection`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/config/MushexProperties.java) (`services/mushex-service`) | Safety switch; when `false`, every intent gets SANDBOX with reason `SAFETY_SWITCH_FORCED_SANDBOX`. |
| `mushex.rail-selection.defaultRail` | `SANDBOX` | `MushexProperties.RailSelection` | Conservative: no auto-routing to a non-SANDBOX rail without an explicit caller preference. |
| `mushex.rail-selection.allowDirectGateway` | `false` | `MushexProperties.RailSelection` | Mode A (direct gateway) is opt-in per environment. |
| `mushex.rail-selection.allowSandboxFallback` | `true` | `MushexProperties.RailSelection` | Operators may set to `false` to force hard rejection on unavailable preferred rails. |

No new secrets, credentials, or live payment-provider configuration introduced.

## 7. Backward compatibility

- **`POST /mushex/v1/payment-intents`** — callers that omit the three new fields keep their pre-Stage-3.5 observable behaviour. `CreateIntentRequest` Java record has a backward-compatible 7-arg secondary constructor; three positional callers in `PaymentIntentIntegrationTest` continue to compile unchanged.
- **`INTENT_CREATED` outbox event** — gains three keys (`effectiveRail`, `preferredRail`, `railSelectionReason`). Per the event-versioning rule in `docs/registry/README.md`, consumers MUST tolerate unknown keys. No keys removed or renamed.
- **`payment_intents.metadata`** — gains a reserved `rail_selection` key. Existing keys are preserved. Non-object metadata is left as-is (rail-selection then visible only on the outbox event).
- **No database schema change.** All persistence uses the existing JSONB column.
- **Wallet contract** — upstream-success shape unchanged. Failure mode: when `impilo.wallet.allow-local-fallback=false` (the production default) BFF emits HTTP 503 with stable code `WALLET_UPSTREAM_UNAVAILABLE` instead of fabricating data; this is the documented, intended change.
- **Mobile citizen wallet** — old builds keep working through the legacy `WellnessServiceProxyController` whitelist for `/internal/v1/mobile/citizen/wallet/**` and the legacy `CitizenMyLifeController` wallet endpoints in `wellness-service`. New builds use the canonical plane.
- **`AdapterType` enum** — unchanged. `PaymentRailAdapter` SPI — unchanged.

## 8. Deployment notes

1. **No database migration is required by Stages 1 through 3.5.** Flyway state is unchanged from the pre-Stage-1 baseline.
2. **No environment variables are required to be set** for safe production behaviour. The defaults (`impilo.wallet.allow-local-fallback=false`, `mushex.rail-selection.allowDirectGateway=false`, `mushex.rail-selection.defaultRail=SANDBOX`) are already production-conservative.
3. **`spring.profiles.active=test`** continues to set `impilo.wallet.allow-local-fallback=true`. Production must not have the test profile active.
4. **Roll-back is purely a code rollback** — there is no schema state to revert. The `payment_intents.metadata.rail_selection` keys become unused but harmless; the three new outbox keys are likewise ignored by older consumers.
5. **Sidecar build pipelines remain in place.** They emit deprecation notices via `DEPRECATED.md` but their CI jobs are not yet retired.

## 9. Smoke tests (manual checklist)

Operators verifying a deployment should confirm:

- [ ] `GET /internal/v1/wallet/me` with valid trust headers returns either upstream wallet shape (success) or `{ "error": { "code": "WALLET_UPSTREAM_UNAVAILABLE", ... } }` with HTTP 503 (when upstream is down and fallback is disabled in prod).
- [ ] `POST /mushex/v1/payment-intents` with the legacy 7-field body returns 201 and the response `data.metadata` contains a `rail_selection` block whose `effective_rail=SANDBOX` and `reason=DEFAULT_NO_PREFERENCE`.
- [ ] `POST /mushex/v1/payment-intents` with `preferredRailAdapter="BITCOIN"` returns HTTP 400 with `error.code=RAIL_ADAPTER_UNKNOWN`.
- [ ] `POST /mushex/v1/payment-intents` repeated with the same `idempotencyKey` returns the original intent (same `intentId`) with its original metadata unchanged.
- [ ] `/finance/costa` resolves in `one-ui-shell` for a FINANCE-role user; tariff / cost-event / service-access counts come from the listed BFF routes or render an honest "could not load" message.
- [ ] `/finance/mushex-platform` resolves in `one-ui-shell` for a FINANCE-role user; **no write buttons** are visible.
- [ ] Mobile citizen wallet section loads via `/internal/v1/wallet/me`; on upstream failure the UI shows the `ErrorState` for `WALLET_UPSTREAM_UNAVAILABLE` rather than `balance = 0`.
- [ ] The three deprecated sidecar folders still contain a `DEPRECATED.md` file and their source is unchanged.

## 10. Remaining gated items (deferred by design)

| Item | Why deferred | Gate to release |
| ---- | ------------ | --------------- |
| Retire `ui/mushex-finance-console`, `ui/mushex-ops-console`, `ui/mushex-payer-portal` folders | Sidecars retained as parity references; deletion requires telemetry confirming no active use. | Phase 7 retirement-readiness gate; telemetry evidence; rollback plan; release note. |
| Retire `WellnessServiceProxyController` whitelist entry for `/internal/v1/mobile/citizen/wallet/**` and `CitizenMyLifeController` legacy wallet endpoints | Older mobile builds may still rely on them during rollout. | Phase 7; telemetry confirming no installed mobile client uses them; compatibility window passed. |
| Live external rail credential handling | Out of scope; requires security/procurement review. | Future stage with explicit business approval. |
| MusheX platform admin write actions (credit/debit, card block, reversal execution, payout release, gateway-config change) | No safety design yet (role gating, step-up auth, dual control, reason capture, audit immutability, rate/amount limits, break-glass). | Phase 4 safety design + governance review. |
| Tenant-/facility-scoped `RailSelectionPolicy` and real-time adapter `isAvailable()` health signal | Adapters today are stubs; tenant store not designed. | Phase 2 (readiness) + Phase 3 (attempt-time) + later phases. |
| Attempt-time enforcement of `metadata.rail_selection.effective_rail` | Carried forward as Phase 3 work. | Phase 3 design + tests. |

## 11. Documentation drift fixed in Phase 1

Only one small drift was found and corrected during this audit:

- [`docs/architecture/vnext-component-catalog.md`](../architecture/vnext-component-catalog.md) **§2.4 Finance UIs** — the three deprecated MusheX sidecar rows (`mushex-finance-console`, `mushex-ops-console`, `mushex-payer-portal`) now carry an inline deprecation note pointing at the canonical `ui/one-ui-shell` finance pages and at the corresponding `DEPRECATED.md` files. The `costa-console` row was also tagged as legacy with a pointer to `/finance/costa`. **No table column added, no row removed.**

All other audited surfaces (routes registry, OpenAPI, doctrine, audit, downstream-route-map, sidecar markers, test inventories) are consistent with the implementation state.

## 12. Confirmation checklist (from the Phase 1 task list)

| # | Item | Verified | Evidence |
| - | ---- | -------- | -------- |
| 1 | Wallet fallback production default is `false`. | ✓ (superseded — fabrication now fully removed) | Originally gated by `BffWalletProperties.allowLocalFallback=false`. The in-memory wallet fabrication and the `BffWalletProperties` flag were subsequently deleted entirely (paydown WS-C): `WalletController` now always fails clean with `503 WALLET_UPSTREAM_UNAVAILABLE` on upstream outage in every profile; mushe-wallet-service is the sole owner. |
| 2 | Mobile uses `/internal/v1/wallet/**`. | ✓ | `apps/mobile/citizen-app/src/services/walletService.ts` `const V1 = "/internal/v1/wallet";`. `financeService.ts` `const WALLET = "/internal/v1/wallet";`. |
| 3 | Legacy wallet routes remain available. | ✓ | `services/wellness-service/.../CitizenMyLifeController.java` and `services/experience-bff/.../WellnessServiceProxyController.java` are both present and unchanged in the current change-set. |
| 4 | `/finance/costa` resolves. | ✓ | `ui/one-ui-shell/src/app/finance/costa/page.tsx` exists; registered in `src/lib/routes.ts` with `requiredRole: "FINANCE"`. |
| 5 | `/finance/mushex-platform` resolves. | ✓ | `ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx` exists; registered in `src/lib/routes.ts` with `requiredRole: "FINANCE"`; no write buttons in the page source (only nav links). |
| 6 | Rail-selection OpenAPI changes are additive. | ✓ | `CreateIntentRequest.required: [sourceType, sourceId, amount, idempotencyKey]` unchanged (line 433 of `contracts/openapi/mushex.openapi.yaml`); three new fields appended after `metadata`; new `RailSelection` schema is reference-only. |
| 7 | Idempotency replay does not re-run selection. | ✓ | `PaymentIntentServiceTest.createIntent_idempotencyReplay_returnsExisting_withoutRerunningSelection` asserts that a second call with the same idempotency key returns the original intent unchanged and does not call `intentRepository.save` or `outboxRepository.save`. Behaviour matches the design at §9. |
| 8 | Sidecar source code unchanged except `DEPRECATED.md` markers. | ✓ | `git status -s -- ui/mushex-*-console` shows only `?? DEPRECATED.md` rows. The last commit touching sidecar source (`7c5b67de`) predates this change-set. |
| 9 | No MusheX admin write actions exist in one-ui-shell. | ✓ | `ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx` contains zero `apiClient.{post,put,patch,delete}` calls; the `actions` array only carries cross-page navigation `href`s. |

## 13. What changes (and what does **not**) in Phase 1

- **No new runtime behaviour** is introduced by Phase 1 itself. The one file edit (component catalog) is documentation.
- **No code in `services/`, `apps/`, `ui/`, `contracts/`, or `apps/mobile/`** was modified by Phase 1.
- **No tests were modified** by Phase 1.
- **Subsequent phases** (Phase 2 onward) carry runtime work. Each phase will batch its own audit → smallest coherent slice → tests → docs and produce its own Batch Report.

## Related

- Doctrine: [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md)
- Audit: [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md)
- Design: [`docs/design/g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md)
- BFF route map: [`docs/architecture/experience-bff-downstream-route-map.md`](../architecture/experience-bff-downstream-route-map.md)
- Component catalog: [`docs/architecture/vnext-component-catalog.md`](../architecture/vnext-component-catalog.md)
- Internal BFF routes index: [`docs/architecture/experience-bff-internal-routes.md`](../architecture/experience-bff-internal-routes.md)
