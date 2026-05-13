# Experience BFF — downstream route map

Authoritative **base URLs** and defaults live in:

- [`services/experience-bff/src/main/resources/application.yml`](../../services/experience-bff/src/main/resources/application.yml) — `impilo.services.*`
- [`ServiceClientConfig.ServiceEndpoints`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/ServiceClientConfig.java) — Java fallbacks for the same keys
- [`ClinicalPlatformProperties`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/ClinicalPlatformProperties.java) — `impilo.clinical-platform.base-url` (Clinical Knowledge Platform)

Trust headers from inbound requests are copied onto outbound `RestTemplate` calls by `trustHeaderForwardingInterceptor()` (see `ServiceClientConfig`).

## `impilo.services` → downstream (default localhost)

| Property | Default base URL | Typical downstream |
|----------|------------------|----------------------|
| `pct-base-url` | `http://localhost:8088` | PCT |
| `oros-base-url` | `http://localhost:8089` | OROS |
| `pharmacy-base-url` | `http://localhost:8096` | Pharmacy |
| `butano-base-url` | `http://localhost:8090` | BUTANO (HAPI host) |
| `msika-base-url` | `http://localhost:8086` | MSIKA |
| `msika-flow-base-url` | `http://localhost:8100` | Msika Flow |
| `mushex-base-url` | `http://localhost:8102` | MUSheX |
| `vito-base-url` | `http://localhost:8082` | VITO |
| `tuso-base-url` | `http://localhost:8084` | TUSO |
| `varapi-base-url` | `http://localhost:8083` | VARAPI |
| `document-store-base-url` | `http://localhost:8093` | Document Store |
| `costa-base-url` | `http://localhost:8101` | Costing engine (COSTA) |
| `coverage-base-url` | `http://localhost:8140` | Coverage |
| `surveillance-base-url` | `http://localhost:8180` | Surveillance |
| `campaigns-base-url` | `http://localhost:8190` | Campaigns |
| `indawo-base-url` | `http://localhost:8150` | INDAWO |
| `data-governance-base-url` | `http://localhost:8220` | Data governance |
| `landela-base-url` | `http://localhost:8092` | Landela adapter |
| `notification-base-url` | `http://localhost:8200` | Notification |
| `credential-base-url` | `http://localhost:8094` | Credential verification (Landela suite) |
| `fhir-base-url` | `http://localhost:8090/fhir` | FHIR root on BUTANO host |
| `fhir-gateway-base-url` | `http://localhost:8091` | FHIR Gateway |
| `search-base-url` | `http://localhost:8230` | Search |
| `forms-base-url` | `http://localhost:8240` | Forms |
| `rules-base-url` | `http://localhost:8241` | Rules |
| `workflow-base-url` | `http://localhost:8250` | Workflow |
| `guidance-base-url` | `http://localhost:8260` | Guidance |
| `integration-hub-base-url` | `http://localhost:8110` | Integration Hub |
| `mvumo-base-url` | `http://localhost:8195` | **Mvumo** (sovereign Ring-0 service, same integration pattern as `tshepo-*` / `vito` URLs) — `/internal/v1/mvumo/**` via `MvumoServiceProxyController`; `EhrPatientSummaryController` also calls `.../mvumo/consent-summary` for chart surfacing |

Environment variable: `MVUMO_BASE_URL` (see `application.yml`), alongside `TSHEPO_*`, `VITO_BASE_URL`, etc. UIs use the BFF only: `GET /internal/v1/summary/patient/{patientId}` (PCT + `consentSummary`) or `/internal/v1/mvumo/**` through the same proxy pattern as other sovereigns.

Ports should match [`docs/runbooks/port-allocation.md`](../runbooks/port-allocation.md) and [`docs/registry/services-registry.yaml`](../registry/services-registry.yaml); if YAML and BFF defaults diverge, treat the port runbook + registry as the reconciliation target.

## Typed HTTP clients (`experience.client`)

| Java class | Role |
|------------|------|
| `ButanoServiceClient` | BUTANO / SHR proxies |
| `ClinicalKnowledgePlatformClient` | Clinical Knowledge Platform (`impilo.clinical-platform`) |
| `CostaServiceClient` | COSTA costing engine |
| `CoverageServiceClient` | Coverage |
| `DocumentServiceClient` | Document Store |
| `ExtensionServiceClient` | Forms + Rules (two base URLs inside one component) |
| `FhirGatewayServiceClient` | FHIR Gateway |
| `FhirPublisher` | FHIR write path helper |
| `GuidanceServiceClient` | Guidance |
| `MsikaFlowServiceClient` | Msika Flow |
| `MsikaServiceClient` | MSIKA |
| `MushexServiceClient` | MUSheX |
| `OrosServiceClient` | OROS |
| `PctServiceClient` | PCT |
| `PharmacyServiceClient` | Pharmacy |
| `SearchServiceClient` | Search |
| `TusoServiceClient` | TUSO |
| `VarapiServiceClient` | VARAPI |
| `VitoServiceClient` | VITO |
| `CredentialServiceClient` | Credential verification |
| `IntegrationHubServiceClient` | Integration Hub (routes, dispatch, dead letters, mapping templates) |

## Deliberate proxies (no dedicated `*Client` bean)

- **Public health:** `PublicHealthController` uses `RestTemplate` against `surveillance-base-url`, `campaigns-base-url`, and `indawo-base-url` (see controller and `application.yml`).

## Wallet local-fallback configuration (Stage 3.1 + Stage 3.1.1 — implemented)

`WalletController` exposes `/internal/v1/wallet/**` and calls `MusheWalletServiceClient`. Doctrine (see [`../doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md), *Wallet local-fallback principle*) is wired into the BFF as follows:

- **Property:** `impilo.wallet.allow-local-fallback`. Default `false`. Environment override: `IMPILO_WALLET_ALLOW_LOCAL_FALLBACK`.
- **Binding class:** [`BffWalletProperties`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/BffWalletProperties.java) (`@ConfigurationProperties(prefix = "impilo.wallet")`); registered in [`ExperienceBffApplication`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/ExperienceBffApplication.java).
- **`application.yml`:** flag defaults to `false` (production-safe).
- **`application-test.yml`:** flag set to `true` so in-JVM tests / golden paths keep their existing in-memory wallet behaviour.
- **Upstream success:** unchanged. The upstream response is forwarded as before.
- **Upstream failure (exception **or** null) with flag `false`:** HTTP **503** with stable error code `WALLET_UPSTREAM_UNAVAILABLE` and a `meta` block containing `request_id` and `correlation_id`. Callers can distinguish "wallet service is down" from "wallet not found" by matching the `code`. The fallback `WARN` line emitted is `WALLET UPSTREAM UNAVAILABLE …`.
- **Upstream failure with flag `true`:** existing in-memory fallback shape is returned, and a `WARN` log line with stable marker `WALLET FALLBACK ACTIVATED` is emitted (carrying `operation`, `reason`, `requestId`, `correlationId`). The marker is the grep/SIEM handle for detecting doctrine-violating production configurations.

**Scope of the gate (Stage 3.1):** `GET /internal/v1/wallet/me` and the `MUSHE_WALLET` branch of `POST /internal/v1/wallet/pay` — the two paths that today exercise `MusheWalletServiceClient`. Non-wallet payment methods (`CASH`, `ECOCASH`, `BANK_TRANSFER`, etc.) are unaffected by the gate.

**Scope of the gate (Stage 3.1.1, audit gap G-5.1):** the three read endpoints that previously synthesised from in-process state are now upstream-first. Each first resolves the wallet via `MusheWalletServiceClient.getWalletByOwner("PERSON", actorId)` (reusing the same private `WalletController.lookupWalletByOwner` helper as `/me`) and then calls the specific upstream operation:

| BFF endpoint | Upstream call | Notes |
|--------------|---------------|-------|
| `GET /internal/v1/wallet/me/balance` | `MusheWalletServiceClient.getBalance(walletId)` | Upstream `JsonNode` is forwarded unchanged inside `{ data, meta }`. |
| `GET /internal/v1/wallet/me/transactions` | `MusheWalletServiceClient.getTransactions(walletId, 0, 50)` | Page/size defaults are internal; no public query-parameter contract change. |
| `GET /internal/v1/wallet/me/funding-sources` | `MusheWalletServiceClient.listFundingSources(walletId)` | Upstream payload (`JsonNode`, typically an array) is forwarded unchanged. |

Failure handling on all three matches Stage 3.1 exactly: flag-off → 503 + `WALLET_UPSTREAM_UNAVAILABLE`; flag-on → existing in-memory shape + `WALLET FALLBACK ACTIVATED` audit log.

The remaining wallet endpoints (`/payment-methods` and the merchant-pay branches inside `/pay` that never touch `MusheWalletServiceClient`) are not subject to the gate because they have no upstream call to fail; their shapes are unchanged.

## Mobile citizen wallet — canonical wiring (Stage 3.4B, audit gap G-3 closed)

The Impilo citizen app now reaches the wallet through the canonical `WalletController` plane described above instead of the legacy `WellnessServiceProxyController` forward path. The relevant call sites are:

| Mobile call site | Canonical BFF endpoint | Downstream |
|------------------|------------------------|------------|
| [`apps/mobile/citizen-app/src/services/walletService.ts`](../../apps/mobile/citizen-app/src/services/walletService.ts) `fetchWallet()` | `GET /internal/v1/wallet/me` | `WalletController` → `MusheWalletServiceClient.getWalletByOwner("PERSON", actorId)` |
| `walletService.ts` `fetchTransactions()` | `GET /internal/v1/wallet/me/transactions` | `WalletController` → `MusheWalletServiceClient.getTransactions(walletId, 0, 50)` |
| [`apps/mobile/citizen-app/src/services/financeService.ts`](../../apps/mobile/citizen-app/src/services/financeService.ts) `fetchBalance()` | `GET /internal/v1/wallet/me/balance` | `WalletController` → `MusheWalletServiceClient.getBalance(walletId)` |
| `financeService.ts` `fetchTransactions()` | `GET /internal/v1/wallet/me/transactions` | as above |
| `financeService.ts` `fetchPendingCharges()` | _(none — stable stub returning `[]`)_ | Reserved for a future COSTA-backed pending-charges route. |

The wallet owner is implied by the `x-actor-id` trust header that the mobile API client (`@impilo/mobile-api-client`) injects from the Keycloak session — there is no `patientId` query parameter on the canonical plane. The mobile API client's `ApiError.fromResponse` was upgraded in the same stage to parse the BFF v1.2 nested error envelope (`{ error: { code, message } }`), so the stable `WALLET_UPSTREAM_UNAVAILABLE` code reaches `WalletSection` and `FinanceSection`, which surface an honest "Wallet temporarily unavailable" `ErrorState` (no fabricated `balance = 0`).

**Legacy retirement deferred.** The `WellnessServiceProxyController` whitelist entry for `/internal/v1/mobile/citizen/wallet/**` and the `CitizenMyLifeController` wallet endpoints in `wellness-service` are intentionally retained so older mobile builds continue to function during rollout. Their removal is a follow-up item; it should run only after telemetry confirms that no installed mobile client still hits the legacy path. New mobile builds resolve the wallet through the canonical plane on day one.

## MusheX payment-intents — rail-selection on create (Stage 3.5, audit gap G-4 closed)

The wire shape of `POST` requests forwarded through the BFF to MusheX `/mushex/v1/payment-intents` is now an explicit superset of the pre-Stage-3.5 contract: callers (BFF clients and `MushexServiceClient` callers) may append three optional fields to the `CreateIntentRequest` body — `preferredRailAdapter` (enum: `MOBILE_MONEY` / `BANK_TRANSFER` / `CARD_GATEWAY` / `SANDBOX`), `allowFallback` (boolean), and `directGatewayAllowed` (boolean). The BFF does not interpret these fields; it forwards the body to MusheX through the existing `MushexServiceClient` path and the MusheX-side `RailSelectionPolicy` makes the decision. Existing BFF callers that omit all three fields keep their current observable behaviour — the MusheX-side default (`defaultRail=SANDBOX`, no fallback applied, no direct-gateway requested) takes over.

Downstream side-effects relevant to BFF consumers:

- **HTTP 400 with stable error codes.** MusheX now rejects `CreateIntentRequest` bodies with an unknown `preferredRailAdapter` value (`error.code=RAIL_ADAPTER_UNKNOWN`) or with a known-but-unregistered preferred rail when `allowFallback=false` (`error.code=RAIL_ADAPTER_UNAVAILABLE`). The standard `ApiResponse.error(...)` envelope is preserved; BFF clients can surface these codes through the same error-handling path used for `WALLET_UPSTREAM_UNAVAILABLE`.
- **Outbox event additions.** The `INTENT_CREATED` payload published to the `event_outbox` (and consumed downstream by audit / finance projections) gains three keys — `effectiveRail`, `preferredRail`, `railSelectionReason` — alongside the existing `intentId` / `sourceType` / `sourceId` / `amount` / `currency` / `facilityId` / `status` keys. Per the event-versioning rule in [`docs/registry/README.md`](../registry/README.md), consumers MUST tolerate unknown keys, so this remains a backward-compatible change.
- **Intent metadata additions.** The intent's `metadata` JSONB column now carries a reserved `rail_selection` object (`effective_rail`, `preferred_rail`, `fallback_applied`, `direct_gateway_requested`, `reason`, `reason_detail`, `selected_at`, `selection_version`). Pre-existing metadata keys are preserved; merging is idempotent and is skipped on idempotency-replay paths.

See [`docs/design/g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md) for the full algorithm, configuration, and test matrix.

## MusheX platform — adapter readiness (Phase 2, audit gap G-7 closed)

The BFF now exposes a read-only adapter readiness snapshot at `GET /internal/v1/finance/mushex-platform/adapter-readiness`. The route is a one-line transparent passthrough through `MushexServiceClient.platformAdapterReadiness()` to the MusheX-side endpoint `GET /mushex/v1/platform/adapter-readiness`. It is gated by the same `FinancePlaneAuthorizationService.assertMushexPlatformAccess("GET")` check as the sibling routes on `FinanceMushexPlatformController`.

Phase 4 follow-on extends that same controller with four additive per-id passthroughs used by the canonical detail pages:

- `GET /internal/v1/finance/mushex-platform/wallets/{walletId}` → `GET /mushex/v1/platform/wallets/{walletId}`
- `GET /internal/v1/finance/mushex-platform/remittance-transfers/{transferId}` → `GET /mushex/v1/remittance-transfers/{transferId}`
- `GET /internal/v1/finance/mushex-platform/card-profiles/{cardProfileId}` → `GET /mushex/v1/platform/card-profiles/{cardProfileId}`
- `GET /internal/v1/finance/mushex-platform/reversals/{reversalId}` → `GET /mushex/v1/platform/reversals/{reversalId}`

All four are read-only passthroughs; no write path changed. Missing or cross-tenant records are normalised by MusheX to `404 PLATFORM_RECORD_NOT_FOUND` and forwarded unchanged by the BFF.

The response is an `ApiResponse<List<AdapterReadiness>>` envelope with one row per `AdapterType` (`MOBILE_MONEY`, `BANK_TRANSFER`, `CARD_GATEWAY`, `SANDBOX`), each carrying:

- `adapterType` — the rail enum value.
- `status` — one of `NOT_REGISTERED`, `DISABLED`, `READY_SANDBOX`, `CREDENTIALS_MISSING`, `READY_LIVE`. (`DEGRADED` is reserved for a future stage and is not produced today.)
- `liveCapable` / `sandboxCapable` — capability booleans for operator dashboards.
- `detail` — a plain-language description suitable for direct surfacing in operator UI.

Key safety properties:

- **No credentials are ever read.** Readiness is derived from `AdapterRegistry` presence and `MushexProperties.adapters.{mobileMoney,bankTransfer,cardGateway,sandbox}` booleans only. The `credentialsConfigured` flag is an operator self-attestation that lives in Spring properties; it is not a credential.
- **No live network calls** are made to any payment provider. The endpoint is a deterministic, in-process computation.
- **No money is moved.** The endpoint is `GET`-only; there is no write counterpart and the `FinanceMushexPlatformController` write methods remain untouched.
- **Conservative defaults.** Production deployments that do not set the new properties report zero `liveCapable` rails; `SANDBOX` reports `READY_SANDBOX`.

The canonical web consumer is the read-only "Platform routing & gateway readiness" table on [`/finance/mushex-platform`](../../ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx); see [`docs/design/phase-2-adapter-readiness.md`](../design/phase-2-adapter-readiness.md) for the full state machine and the future-hooks (`DEGRADED`, tenant-scoping, runtime health probing) deferred for later stages.

## COSTA finance lifecycle — encounter invoice listing (Phase 5 follow-on, audit gap G-10)

Phase 5 follow-on adds one additive billing-workspace passthrough:

- `GET /internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=...`
  → `GET /costa/v1/finance/lifecycle/invoices?encounter_id=...`

Phase 5 remaining slice adds two additive MusheX passthroughs for timeline fan-in:

- `GET /internal/v1/finance/payer-ops/payment-intents?sourceType=...&sourceIds=...`
  → `GET /mushex/v1/payment-intents?source_type=...&source_ids=...`
- `GET /internal/v1/finance/settlements?intentIds=...`
  → `GET /mushex/v1/settlements?intent_ids=...`

The upstream endpoint returns encounter invoice rows enriched with invoice payload, `mushex_intent_id` signal (from COSTA handoff), and latest payment signal fields (`payment_status`, `paid_at`). The canonical consumer is the read-only timeline at [`/finance/costa/encounter/[encounterId]`](../../ui/one-ui-shell/src/app/finance/costa/encounter/%5BencounterId%5D/page.tsx), which merges these invoice rows with existing service-access and cost-event rows.

## Coverage / claims / remittance surface coherence (Phase 6, audit gap G-11)

Phase 6 does not add new BFF routes; it wires existing endpoints into canonical one-ui-shell surfaces:

- `/coverage` tabs now consume:
  - `GET /internal/v1/coverage/preauths`
  - `GET /internal/v1/coverage/contributions`
  - `GET /internal/v1/coverage/appeals`
  - `GET /internal/v1/coverage/utilization`
- New read-only finance hub page:
  - `/finance/remittances` consumes `GET /internal/v1/coverage/remittances`
- Claims coherence pass keeps the same payer-claims write routes:
  - `POST /internal/v1/finance/payer-claims/{claimId}/submit`
  - `POST /internal/v1/finance/payer-claims/{claimId}/dispute`
  - but now requires explicit UI confirmation in the canonical `/finance/payer-claims/[claimId]` surface.

## Related

- BFF **`/internal/v1` surface** (generated controller index): [`experience-bff-internal-routes.md`](./experience-bff-internal-routes.md) — `cd scripts/bff-routes && node list-bff-internal-routes.mjs`
- MusheX dual-mode operating doctrine (orchestration vs. direct/default gateway, gateway neutrality): [`../doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) — context for how `mushex-base-url` and `MushexServiceClient` are reached and when MusheX itself acts as the gateway.
- OpenAPI inventory: [`contracts/openapi/`](../../contracts/openapi/)
- Completeness dimensions: [`scripts/completeness/`](../../scripts/completeness/)
- Roadmap: [`docs/roadmaps/agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md)
