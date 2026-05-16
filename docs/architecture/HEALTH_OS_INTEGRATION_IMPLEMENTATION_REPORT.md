# Health OS — Integration, Marketplace & Service-to-Service Implementation Report

> Doctrine implemented: **Impilo vNext operates as a Health Operating System
> — sovereign at the core, modular at the edges, extensible by design,
> secure by default, and governed through trusted registries, consent,
> policy, contracts, audit and marketplace-based activation.**

This report covers the audit + implementation across the five governed
caller classes (internal S2S, experience layer, external institutional
integrations, marketplace capabilities, citizen-mediated proxy) and the
Health OS App Marketplace (Msika Apps).

---

## 1. Current state found in the repo

Pre-existing assets that were preserved and extended (no duplication):

| Asset                                      | Role                                                  |
|--------------------------------------------|-------------------------------------------------------|
| `services/integration-hub`                 | Eventing / outbox / dispatch / connector backbone     |
| `services/msika-service`                   | Commerce product & services registry                  |
| `services/msika-flow-service`              | Commerce workflow orchestration                       |
| `services/tshepo-*-service` (×6)           | Trust layer (identity, consent, authz, audit, keys)   |
| `services/channels-service`, `notification-service`, `share-slip-service` | Comms Hub family                |
| `services/mushex-service`, `mushe-wallet-service`, `general-ledger-service` | MUSHEX payments/claims         |
| `services/costing-engine-service`          | COSTA                                                 |
| `services/learning-service`                | FUNDO                                                 |
| `services/ndila-service`                   | Spatial intelligence                                  |
| `services/nhume-service`, `dispatch-service` | Delivery / fleet                                    |
| `services/llm-orchestration-service`       | Nompilo backend                                       |
| `services/fhir-gateway-service`, `connector-fhir-adapter`, `pacs-adapter-service`, `pharmacy-elmis-adapter`, `inventory-elmis-adapter`, `landela-adapter-service` | Existing canonical adapters |
| `services/audit-ledger-service`, `observability-service` | Audit & ops                              |
| `services/developer-portal-service`        | Developer portal backend (extended)                   |
| `libs/tech-companion`                      | Trust header / RequestContext propagation             |
| `libs/shared-kernel-java`                  | Shared domain primitives                              |
| `ui/one-ui-shell/src/app/marketplace`     | Commerce-style marketplace UI (preserved)             |
| `apps/mobile/citizen-app`, `apps/mobile/provider-app` | Mobile shells with Nhume, Public Health, Telehealth etc. (preserved) |

Gaps identified by the audit:

* No formal **capability marketplace** (apps / plugins / extensions /
  connectors / adapters / packs / AI skills / device integrations) governed
  separately from commerce Msika.
* No **governance plane** for external app registration, integration
  contracts, S2S contracts, event catalogue, webhook subscriptions.
* No **canonical port interfaces** in code (only specific adapter services).
* No **role/facility-aware launcher** sourced from real marketplace state.
* Inconsistent S2S header propagation — no `X-Request-Source`,
  `X-Service-Id`, `X-External-App-Id` standardisation.
* No **AI skill manifest** doctrine for Nompilo extensibility.

---

## 2. Doctrine, taxonomy, contracts and schemas added

| Artefact                                                          | Path                                                                                          |
|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Extensibility doctrine                                            | `docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md`                                          |
| Canonical TS taxonomy + manifest types                            | `contracts/health-os-extensibility.ts` (+ `AISkillManifest` added in this pass)              |
| JSON Schemas: app / extension / plugin / connector / adapter / workflow-pack / content-pack / **ai-skill** / device-integration / integration-contract / s2s-contract / webhook-subscription / external-event-subscription | `contracts/schemas/*.schema.json` |
| OpenAPI: `msika-apps`, `integration-governance`, plus updates to existing contracts | `contracts/openapi/*.yaml`                                                                |
| AsyncAPI: `health-os-events`                                      | `contracts/asyncapi/*.yaml`                                                                  |
| AI Skill manifest (sample): Nompilo Marketplace Helper            | `contracts/ai-skills/nompilo-marketplace-helper.skill.json`                                  |
| Integration categories, manifest, webhook, go-live & security guides | `docs/developer/*.md`                                                                     |

---

## 3. Internal service-to-service (S2S) — patterns added

* Standardised trust headers extended in `libs/tech-companion`:
  `X-Service-Id`, `X-Service-Name`, `X-Service-Version`, `X-Request-Source`,
  `X-External-App-Id`, `X-Integration-Type`, `X-Integration-Version`,
  `X-Idempotency-Key`, plus the existing `X-Tenant-Id`, `X-Correlation-Id`,
  `X-Actor-Id`, `X-Actor-Type`, `X-Purpose-Of-Use`, `X-Facility-Id`,
  `X-Workspace-Id`, `X-Shift-Id`.
* `S2SHeaderEnricher` propagates these headers on outbound S2S calls and
  injects the current service's identity automatically.
* `RequestContextHolder.current()` exposed an `Optional<RequestContext>` so
  background workers can attach context cleanly.
* `experience-bff` `ServiceClientConfig.trustHeaderForwardingInterceptor`
  now forwards all new headers and sets `X-Service-Id: experience-bff`.
* `ServiceToServiceContract` registry lives in `integration-hub` with a
  runtime `check` endpoint at
  `GET /internal/v1/s2s-contracts/check?caller=&callee=&requestSource=`.
* `RequestSource` enum distinguishes `HUMAN`, `SYSTEM`, `SCHEDULED_JOB`,
  `BACKGROUND_WORKER`, `EVENT_CONSUMER`, `AI_ASSISTED`, `EXTERNAL_APP`.

---

## 4. External integrations — governance plane added

`integration-hub` extended with a thin governance CRUD plane:

* **Entities** (`governance.domain`): `ExternalApplicationEntity`,
  `IntegrationContractEntity`, `ServiceToServiceContractEntity`,
  `WebhookSubscriptionEntity`, `ExternalEventSubscriptionEntity`,
  `EventCatalogueEntryEntity`.
* **Service**: `GovernanceService` (registration, suspension, contract
  lifecycle, S2S contract authorisation lookup, webhook subscription, event
  catalogue listing).
* **Controllers** (`governance.api`): `/internal/v1/external-apps`,
  `/internal/v1/integration-contracts`, `/internal/v1/s2s-contracts`,
  `/internal/v1/webhook-subscriptions`, `/internal/v1/event-catalogue` —
  every endpoint protected with `@PreAuthorize` role checks and
  `@EnableMethodSecurity(prePostEnabled = true)` is now wired.
* **WebhookSigner**: HMAC-SHA256 signer + constant-time verifier with
  300-second clock-skew rejection. Test signing surface at
  `/internal/v1/webhook-subscriptions/{id}/test`.
* **V004 migration** under `integration-hub/src/main/resources/db/migration/`.

---

## 5. Health OS App Marketplace — new service

`services/msika-apps-service` (port `8181`) — distinct from `msika-service`
(commerce) and `msika-flow-service` (commerce workflows):

* **Entities**: `PublisherEntity`, `MarketplaceItemEntity`,
  `ActivationRequestEntity`, `InstallationEntity`, `AuditEventEntity`.
* **Service**: `MarketplaceService` — catalogue search, activation request
  + decision, installation configure/activate/suspend lifecycle,
  role-/facility-aware launcher, audit trail.
* **Controllers**: `/internal/v1/marketplace/items`, `/publishers`,
  `/activation-requests`, `/installations`, `/launcher`, `/audit` —
  all protected with `@PreAuthorize` role checks and
  `@EnableMethodSecurity(prePostEnabled = true)`.
* **Migrations**: `V001__init.sql` (full schema), `V002__seed_marketplace_items.sql`
  (sample publishers, apps, connectors, adapters, workflow/content packs, AI
  skills).
* **Nompilo tool surface**: `NompiloMarketplaceTools` deterministic Java
  methods invocable from Nompilo (`findMarketplaceCapability`,
  `explainCapabilityStatus`, `listInstalledCapabilities`,
  `summarizePendingApprovals`, `troubleshootIntegration`).

---

## 6. Responsibility split

| Concern                              | Owner                                                                |
|--------------------------------------|----------------------------------------------------------------------|
| Commerce product & services registry | `msika-service`                                                      |
| Commerce workflow orchestration      | `msika-flow-service`                                                 |
| **Capability marketplace** (apps/plugins/extensions/connectors/adapters/packs/AI skills/device integrations) | **`msika-apps-service`**          |
| Technical onboarding, credentials, scopes, contracts, webhooks, event catalogue, S2S registry | `integration-hub` (governance plane)        |
| Trust / consent / policy / authz / audit decisioning | `tshepo-*-service`                                            |
| AI assistant / discovery / explanation                | `llm-orchestration-service` (Nompilo) + per-service tools     |
| Audit ledger / observability         | `audit-ledger-service`, `observability-service`                      |

---

## 7. Canonical adapter ports (anti-vendor-lock-in)

Added under
`libs/shared-kernel-java/src/main/java/zw/gov/mohcc/impilo/sharedkernel/integration/ports/`:

* `ImagingIntegrationPort` · `LaboratoryIntegrationPort` ·
  `LogisticsIntegrationPort` · `TelemedicineIntegrationPort` ·
  `OmnichannelIntegrationPort` · `PaymentIntegrationPort` ·
  `MapsIntegrationPort` · `AIProviderPort` · `DeviceIntegrationPort`
* `AdapterDescriptor`, `Adapter`, `CanonicalReferences` co-types

The experience layer and BFF call canonical Impilo services that depend on
these ports. Vendor-specific adapters (PACS, LIS, eLMIS, …) live behind
their port and are registered as marketplace `ADAPTER`s.

---

## 8. Eventing / webhooks

* Internal events flow through the existing Kafka outbox in
  `integration-hub`; sovereign services subscribe directly.
* External events: `EventCatalogueEntry.classification ∈ {INTERNAL_PLATFORM,
  EXTERNALLY_PUBLISHABLE}` is the gate.
* External app webhook deliveries are signed with HMAC-SHA256 (see
  `WebhookSigner` + `docs/developer/WEBHOOK_GUIDE.md`), retried with
  exponential backoff up to `deadLetterAfterRetries`, and replay-protected
  via `X-Impilo-Webhook-Delivery-Id`.
* Inbound webhooks from partners are verified at the gateway edge.

---

## 9. Experience BFF — endpoints added

`services/experience-bff`:

* `MsikaAppsClient` → calls `msika-apps-service`.
* `IntegrationRegistryClient` → calls `integration-hub` governance.
* `HealthOsMarketplaceController` exposes
  `/internal/v1/marketplace/items`, `/launcher`, `/activation-requests`,
  `/installations`, plus admin `/integration` governance read paths.
* `ServiceClientConfig.ServiceEndpoints` extended to include
  `msikaAppsBaseUrl`; trust-header interceptor propagates the full new
  header set with the BFF's own service identity.

---

## 10. UI screens added or updated

`ui/one-ui-shell` (Next.js, App Router):

| Route                                                  | Purpose                                                      |
|--------------------------------------------------------|--------------------------------------------------------------|
| `/marketplace/apps`                                    | Capability marketplace catalogue (browse / filter)           |
| `/marketplace/apps/[itemCode]`                         | Capability detail + request-activation form                  |
| `/marketplace/apps/admin/activation`                   | Approvals queue for activation requests                      |
| `/marketplace/apps/admin/installations`                | Installations dashboard with activate / suspend lifecycle    |
| `/marketplace/apps/integration`                        | Integration operations: external apps + event catalogue      |
| `/developer/event-catalogue`                           | Internal-vs-publishable event catalogue                      |
| `ShellStartMenu`                                       | Dynamically renders installed marketplace apps via launcher  |
| `NompiloGlobalCommandBar`                              | Surfaces Health OS marketplace suggestions in command bar    |
| `useHealthOsLauncher` hook                             | Single source for launcher / catalogue / approvals / ops     |

The pre-existing commerce marketplace (`/marketplace`) was preserved.

---

## 11. Mobile updates

* `apps/mobile/citizen-app`:
  * New service `healthOsLauncherService.ts` (launcher + activation request).
  * New `HealthOsAppsScreen` mounted as the **Apps** tab inside the existing
    `MarketplaceScreen` (Browse / Requests / Deliveries / Cart / **Apps**).
* `apps/mobile/provider-app`:
  * New service `healthOsLauncherService.ts`.
  * New `HealthOsAppsScreen` wired in as the **Apps** tab in `ProviderTabs`.
  * `ProviderTabKey` extended with `"apps"`.

All existing screens (Nhume tracking, Telehealth, Personal health, Public
Health, Marketplace commerce, Provider clinical tools, Courier tabs, etc.)
remain untouched and continue to function.

---

## 12. Security controls added

* `@EnableMethodSecurity(prePostEnabled = true)` in
  `msika-apps-service.SecurityConfig` and `integration-hub.SecurityConfig`.
* `@PreAuthorize("hasAnyRole(...)")` on every marketplace + governance
  endpoint, scoped to role classes (`CITIZEN`, `PROVIDER`, `FACILITY_ADMIN`,
  `DISTRICT_ADMIN`, `PROVINCIAL_ADMIN`, `NATIONAL_ADMIN`,
  `MARKETPLACE_ADMIN`, `INTEGRATION_ADMIN`, `OPERATIONS`, `AUDITOR`,
  `SYSTEM_ADMIN`, `DEVELOPER`).
* Webhook signature verifier with constant-time comparison + skew rejection.
* AI Skill manifest doctrine: `prohibitedAutonomousActions`,
  `requiresConfirmation`, `phiAccess` flags.
* Frontend / backend plugin runtime safety documented in
  `HEALTH_OS_EXTENSIBILITY_DOCTRINE.md` and `SECURITY_CHECKLIST.md`.

---

## 13. Tests added

| Test                                                                | What it asserts                                                                          |
|---------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `integration-hub/.../WebhookSignerTest`                             | Deterministic signing, body-tamper rejection, wrong-secret rejection, clock-skew window, malformed-header tolerance |
| `msika-apps-service/.../MarketplaceLifecycleTest`                   | Catalogue filter, non-APPROVED activation rejection, full request→approve→install→activate→suspend→reactivate, tenant isolation, launcher REQUEST_ACCESS vs INSTALLED states, audit-trail accumulation |
| `msika-apps-service/.../NompiloMarketplaceSkillManifestTest`        | Manifest parses, declares expected stable tool names, references the correct Java impl class, enforces READ_ONLY effects and the non-autonomy doctrine |

Existing test suites (`IntegrationHubServiceTest`,
`IntegrationHubV11ComplianceTest`, `IntegrationHubConnectorTest`,
`IntegrationHubGoldenContractIT`, etc.) are unchanged.

---

## 14. Developer documentation added

| Doc                                                | Purpose                                                            |
|----------------------------------------------------|--------------------------------------------------------------------|
| `docs/developer/HEALTH_OS_INTEGRATION_CATEGORIES.md` | Five governed caller classes + canonical ports                   |
| `docs/developer/MARKETPLACE_MANIFEST_GUIDE.md`     | Marketplace manifest types, common fields, publishing flow, AI-skill non-autonomy doctrine |
| `docs/developer/WEBHOOK_GUIDE.md`                  | Subscribing, signing/verifying, retries, dead-letter, replay protection, inbound webhooks |
| `docs/developer/GO_LIVE_CHECKLIST.md`              | External-app go-live sign-off checklist                            |
| `docs/developer/SECURITY_CHECKLIST.md`             | Hard security rules for every capability class                     |

---

## 15. Known limitations & remaining work

* **TSHEPO policy refs** referenced from AI skill manifests
  (`policy:nompilo.marketplace.helper.v1`) need to be authored inside
  `tshepo-authz-service` policy bundles.
* **Inbound webhook gateway verification** is documented and the verifier is
  in place, but enforcement at every gateway entry point still needs to be
  wired into the FHIR gateway, Envoy/edge config, and any partner-specific
  inbound routes.
* **Marketplace search ranking & recommendation** (role-based "recommended
  for you" tiles) currently returns raw catalogue order; a recommendation
  scorer remains to be added.
* **Marketplace plugin runtime sandbox**: the doctrine is documented but
  the actual extension-frame loader (CSP'd iframe + postMessage RPC) is not
  yet implemented in the shell. Today plugins are limited to backend-only
  capabilities.
* **Citizen-mediated proxy** (`CITIZEN_MEDIATED_PROXY` caller class)
  consent + scope checking endpoints are stubbed in the governance plane;
  the live policy enforcement at the gateway needs the consent service
  integration to be completed.
* **Mobile launcher offline mode**: failure mode is graceful (empty list),
  but a per-user cache of last-known launcher state is still to be added.

## 16. Manual setup required

* Apply the new Flyway migrations in `msika-apps-service` (V001/V002) and
  `integration-hub` (V004). The seed data in V002 is non-PHI sample data.
* Register the BFF and each sovereign service as an
  `ExternalApplication`-equivalent within Keycloak; assign the roles
  enumerated above so `@PreAuthorize` checks pass.
* Provision a vault-kms secret for each `WebhookSubscription`; the
  `signatureSecretRef` field stores the vault reference, never the raw
  secret.
* Add the new role names (`MARKETPLACE_ADMIN`, `INTEGRATION_ADMIN`,
  `OPERATIONS`, `AUDITOR`) to your Keycloak realm if not already present.

## 17. Assumptions made

* Spring Security `hasAnyRole(...)` is the project's standard authorisation
  primitive for HTTP-layer enforcement (matches the pattern in
  `msika-service` controllers).
* Sovereign services accept JWTs issued by the central Keycloak realm
  declared in each service's `application.yml`.
* Kafka-backed outbox in `integration-hub` is the canonical platform event
  bus.
* The shell uses TanStack Query (already present) for the new
  `useHealthOsLauncher` hook.

## 18. Risks identified

* **Role drift** between Keycloak and the `@PreAuthorize` annotations —
  mitigated by enumerating allowed roles in the integration categories
  document.
* **Manifest schema evolution** — bump semver and keep prior schema files
  in `contracts/schemas/` to avoid breaking older installed items.
* **Webhook secret leak via misconfigured logs** — the controller test
  endpoint deliberately uses the sentinel `dev-sandbox-secret`; production
  secrets must always come from vault-kms.
* **Plugin runtime safety** — until the extension-frame loader ships,
  frontend plugins are not yet supported; doctrine prevents accidental
  exposure but reviewers must enforce manually.
* **AI skill drift** — the non-autonomy doctrine is enforced at manifest
  publish time and at runtime by the Nompilo orchestrator; any deviation
  must trigger a marketplace governance review.
