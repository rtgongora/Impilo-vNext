# Health OS Extensibility Doctrine

> **Status:** Canonical. Supersedes ad-hoc references to "Impilo marketplace", "plugins", or "integrations" that do not conform to this taxonomy.
> **Scope:** Defines how the Impilo vNext Health Operating System distinguishes internal sovereign services from external systems, plugins, extensions, apps, connectors, adapters, workflow packs, content packs, AI skills, device integrations, and marketplace listings.
> **Related doctrine:**
> - `docs/doctrine/health-os-doctrine.md` (§8 identifier doctrine, §11 governed-action context)
> - `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md` and `docs/registry/services-registry.yaml`
> - `docs/architecture/SERVICE_TO_SERVICE_TRUST_PATTERN.md`
> - `docs/architecture/CANONICAL_INTEGRATION_PORTS.md`
> - `INTEGRATED_OPERATING_MODEL.md`

---

## 0. One-sentence doctrine

**Impilo vNext operates as a Health Operating System: sovereign at the core, modular at the edges, extensible by design, secure by default, and governed through trusted registries, consent, policy, contracts, audit, and marketplace-based activation.**

This means:

- Internal sovereign services communicate through **internal service-to-service trust contracts** under the Health OS Manifest v1.2 headers, Envoy `ext_authz` → TSHEPO PDP, and the platform event bus.
- External systems, apps, plugins, extensions, connectors, packs, AI skills, and device integrations communicate through the **Integration Service governance plane**, the **Msika Apps capability marketplace**, formal **integration contracts**, **signed webhooks**, and **scoped, purpose-bound, auditable** access.
- Nothing third-party is ever treated as if it were internal. Nothing internal is ever exposed as if it were a casual public API.

---

## 1. Five classes of caller

| # | Class | Trust posture | Example caller | Entry path |
|---|-------|---------------|----------------|------------|
| 1 | **Internal sovereign service** | Workload identity, full S2S trust contract, internal event bus access. | `pct-service` → `butano-service` | Internal cluster mesh; tech-companion S2S headers; Envoy internal route |
| 2 | **Experience layer (web / mobile / kiosk)** | User-bound session + Health OS v1.2 headers; calls always land at `experience-bff`. | `one-ui-shell`, citizen-app | BFF only — never direct service access |
| 3 | **External system (institutional)** | Registered organisation, integration contract, OAuth2 client-credentials or mTLS, scoped + purpose-bound. | DHIS2, external LIMS, external PACS, Mosip CRVS | `integration-hub` route + signed gateway |
| 4 | **Marketplace app / extension / connector / pack / skill / device** | Registered publisher, approved manifest, role-scoped activation, runtime sandbox, signed events. | "ZW PACS Connector v2.1", "Oncology Extension Pack" | Msika Apps activation → Integration contract |
| 5 | **Citizen-mediated proxy** | Person-anchored share grants, narrow scope, time-bound, consent-bound. | Patient-initiated lab result share with external provider | TSHEPO consent → BFF |

A caller may belong to only **one** of these classes per request. Class mixing (e.g. an external app pretending to be an internal service) is a hard violation.

---

## 2. Internal sovereign service registry

The following services are recognised as **Core Platform Services**. They are **not marketplace items**. They cannot be uninstalled. They participate in the Health OS sovereignty fabric directly.

| Plane | Sovereign services |
|-------|--------------------|
| **Trust** | TSHEPO (authz/identity/consent/audit/keys/offline), Mvumo (sovereign consent orchestration), audit-ledger |
| **Registry** | VITO (clients), VARAPI (providers), TUSO (facilities), INDAWO (locations), ZIBO (terminology), product-registry, asset-registry, schema-registry, ai-model-registry |
| **Clinical** | BUTANO (SHR / FHIR), PCT, OROS, Pharmacy, Inpatient, Community, Scheduling, Referral, Forms, Workflow, Clinical Knowledge Platform, Guidance |
| **Data** | NDR, Data Warehouse, Reporting, Surveillance, Search, Analytics Pipeline, Data Ingestion, Data Governance, Data Access Governance |
| **Integration** | **Integration Hub** (runtime route/dispatch/dead-letter/connector), Document, Notification, Channels, Jobs, PACS Adapter, FHIR Gateway, Connector FHIR Adapter, Offline Sync, Offline Edge, IoT Ingestion |
| **Experience** | Experience BFF, one-ui-shell, mobile apps |
| **Enterprise** | Costing Engine (COSTA), Coverage, MUSheX (payments/claims), MUSheX Wallet, General Ledger, HR/Payroll, Procurement, MSIKA, MSIKA Flow, **MSIKA Apps (Capability Marketplace)**, Wellness, Learning (Fundo), Workforce Governance |
| **Logistics / Mobility** | NHUME (dispatch/delivery/fleet), Dispatch, NDILA (geospatial) |
| **AI / Knowledge** | LLM Orchestration, AI Model Registry, Clinical Knowledge Platform, Guidance |
| **Platform Ops** | Observability, Security Hardening, Support, Credential Verification, Identity Assurance, Card Print Agent, Share Slip |

Authoritative machine list: `docs/registry/services-registry.yaml`.

Each sovereign service:

1. Is built from the monorepo `services/<name>-service/` module.
2. Owns its own database schema.
3. Publishes events through the **shared-kernel-java** outbox with `CompanionOutboxPublisher` semantics.
4. Validates inbound requests with the tech-companion `RequestContextHolder` and Manifest v1.2 headers.
5. Has its **service identity** registered in the **Service-to-Service Contract registry** (see §4).
6. Has its **OpenAPI** spec in `contracts/openapi/` and its **AsyncAPI** spec in `contracts/asyncapi/` (or referenced from `contracts/async/impilo-events.asyncapi.yaml`).

---

## 3. Capability classes (extensibility taxonomy)

Everything that extends the Health OS — internal or external — falls into one of nine classes. A single artefact has **exactly one** primary class.

### 3.1 App

A full user-facing capability that appears in the Health OS launcher / workspace switcher / marketplace.

- **Backed by:** an existing or new sovereign service, OR a registered external system.
- **Manifest:** `AppManifest` (`contracts/schemas/app-manifest.schema.json`).
- **Examples:** Telemedicine App, Imaging Viewer App, Logistics App, Claims App, Marketplace Vendor App.

### 3.2 Extension

A bounded capability that augments a sovereign service or workflow (oncology, school health, occupational health, etc.).

- **Backed by:** rules + forms + workflow packs hosted in `forms-service`, `rules-service`, `workflow-service`, plus optional dedicated UI surfaces.
- **Manifest:** `ExtensionManifest`.
- **Examples:** Oncology Extension, School Health Extension, Emergency Response Extension.

### 3.3 Plugin

A small capability that lives inside an existing service or experience surface (a calculator, a widget, an extra triage scorer, a custom dashboard tile).

- **Backed by:** UI component (registered through the Capability Marketplace) and/or rules entry in `rules-service`.
- **Manifest:** `PluginManifest`.
- **Runtime safety:** plugins run inside the approved module/extension frame. See §10.
- **Examples:** EDLIZ EWS Calculator Plugin, Custom Facility KPI Tile.

### 3.4 Connector

A bridge between Impilo and a named external system, exposed as an integration partner. A connector is a **deployable unit** with a vendor and a contract.

- **Backed by:** route definitions in `integration-hub`, an external application registration, an integration contract.
- **Manifest:** `ConnectorManifest`.
- **Examples:** DHIS2 Connector, WhatsApp BSP Connector, EcoCash Mobile Money Connector, Mosip CRVS Connector.

### 3.5 Adapter

A vendor-specific implementation behind a **canonical Impilo integration port**. Adapters are how we **prevent vendor lock-in**: the experience layer talks to the canonical port; the adapter translates to the vendor protocol.

- **Backed by:** a port interface (Java + TS), a chosen adapter implementation, registered via the integration registry.
- **Manifest:** `AdapterManifest`.
- **Examples:** `DicomWebPacsAdapter` behind `ImagingIntegrationPort`; `EcoCashAdapter` behind `MobileMoneyAdapter` behind `PaymentIntegrationPort`.

### 3.6 Workflow Pack

A bundle of pre-defined workflows, queues, forms, dashboards, permissions, and routing rules.

- **Backed by:** content in `workflow-service`, `forms-service`, `rules-service`, plus permissions hints for TSHEPO.
- **Manifest:** `WorkflowPackManifest`.
- **Examples:** Antenatal Care Pack, TB Programme Pack, Pharmacy Dispensing Pack.

### 3.7 Content Pack

Approved content (templates, education, knowledge snippets, campaigns).

- **Backed by:** content stored in `notification-service` (templates), `clinical-knowledge-platform-service` (knowledge), `campaigns-service` (campaigns), `learning-service` (Fundo).
- **Manifest:** `ContentPackManifest`.
- **Examples:** Antenatal SMS Pack, EDLIZ Knowledge Snippets, Public Health Cholera Campaign Pack.

### 3.8 AI Skill

An ability that Nompilo (or another approved AI surface) can perform, scoped to specific roles, data categories, and purposes.

- **Backed by:** `guidance-service`, `llm-orchestration-service`, `ai-model-registry-service`.
- **Manifest:** `AISkillManifest`.
- **Examples:** "Summarise patient journey", "Explain lab result delay", "Draft public-health briefing", "Marketplace troubleshooting helper".

### 3.9 Device Integration

A registered medical, logistics, biometric, IoT, or wearable device integration.

- **Backed by:** `iot-ingestion-service`, `asset-registry-service`, `nhume-service` (vehicle/drone), `wellness-service` (Health Connect).
- **Manifest:** `DeviceIntegrationManifest`.
- **Examples:** Cold-chain Sensor, Glucometer, Vehicle Tracker, Drone Telemetry Adapter.

---

## 4. Service-to-Service contracts (internal trust pattern)

Every internal service-to-service call MUST:

1. Carry the **Health OS Manifest v1.2 headers** (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`) and `Authorization` as defined in `libs/tech-companion/.../CompanionHeaders.java`.
2. Additionally carry **service identity headers** when the caller is itself a service:
   - `X-Service-Id` — registered service identifier from the S2S contract registry.
   - `X-Service-Name` — human-readable name.
   - `X-Service-Version` — semantic version of the calling service.
   - `X-Request-Source` — one of `HUMAN`, `SYSTEM`, `SCHEDULED_JOB`, `BACKGROUND_WORKER`, `EVENT_CONSUMER`, `AI_ASSISTED`, `EXTERNAL_APP`.
3. Be **registered** in the **Service-to-Service Contract registry** (`integration-hub` `/internal/v1/s2s-contracts`). The registry records: caller service, callee service, allowed scopes, allowed operations, allowed event topics, contract version, last verified, owner team, support contact.
4. Be **authorized** by TSHEPO via Envoy `ext_authz` against the registered contract. Unregistered S2S calls are denied at the gateway.
5. Be **auditable** through `audit-ledger-service` with caller service id, request id, decision, and scope.

When there is no human actor, the audit trail distinguishes:

- `HUMAN` — initiated by a logged-in user action
- `SYSTEM` — synchronous platform-internal action (e.g. orchestration kickoff)
- `SCHEDULED_JOB` — recurring batch or scheduled task
- `BACKGROUND_WORKER` — outbox publisher, retry worker, reconciler
- `EVENT_CONSUMER` — Kafka consumer
- `AI_ASSISTED` — AI-mediated action (logged with model id and skill id)
- `EXTERNAL_APP_ORIGINATED` — request was initiated by an external app and the internal service is acting on its behalf

---

## 5. Internal vs external events

The Health OS event fabric is **dual-tier**.

| Tier | Description | Catalogue | Subscribable by |
|------|-------------|-----------|-----------------|
| **Internal events** | The platform's internal nervous system. Full PII and references may travel. | `contracts/async/impilo-events.asyncapi.yaml` and per-plane AsyncAPI files | Internal services only, registered via S2S contract |
| **External publishable events** | A curated, minimised, role-scoped subset of internal events that may be published to approved external apps. | `contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml` | External apps with a webhook subscription approved by Integration Registry |

Rules:

1. External apps **never** subscribe directly to internal Kafka topics. They subscribe to **webhook deliveries** filtered by the Integration Service through registered Webhook Subscriptions.
2. The Event Catalogue (`/internal/v1/event-catalogue`) makes the classification explicit per event: `INTERNAL_ONLY` vs `EXTERNALLY_PUBLISHABLE`.
3. Webhook payloads avoid PII by default — they use stable references (Health ID, encounter ID, etc.) and require the external app to fetch detail through API with their own scopes.
4. Webhook payloads are **signed** (HMAC-SHA256) with the per-subscription secret; external apps MUST verify and MUST reject unverified payloads (see `docs/developer/WEBHOOK_GUIDE.md`).

---

## 6. External application registration (Integration Service governance)

The **Integration Service governance plane** lives inside `integration-hub`. It manages:

1. **External applications** (`/internal/v1/external-apps`) — every external integration partner has a registered application with publisher, technical contact, data protection contact, risk classification, environment, status.
2. **Integration contracts** (`/internal/v1/integration-contracts`) — formal contract per (external app, integration category, environment) tuple with allowed APIs, allowed events, scopes, purpose-of-use, consent basis, signature method, rate limits.
3. **Webhook subscriptions** (`/internal/v1/webhook-subscriptions`) — per external app, list of allowed event topics + delivery URL + signature secret + delivery status.
4. **External event subscriptions** (`/internal/v1/external-event-subscriptions`) — projection of which external app is subscribed to which externally-publishable event topic.
5. **Event catalogue** (`/internal/v1/event-catalogue`) — single canonical list of events with classification, schema link, partner-publishable flag, sensitivity tier.

External apps may not call any sovereign service directly. All access routes through:

```
External App
    │  (OAuth2 client-credentials + integration contract token + signed request + X-External-App-Id)
    ▼
Envoy / API Gateway
    │  (ext_authz → TSHEPO PDP, verifies integration contract scope, purpose, environment)
    ▼
Integration Hub route definition
    │  (transforms, rate-limits, audits)
    ▼
Sovereign service via Internal trust pattern (carrying X-External-App-Id and X-Request-Source=EXTERNAL_APP)
```

Outbound from the Health OS to external apps:

```
Sovereign service publishes internal event
    ▼
Outbox → Kafka
    ▼
Integration Hub external-event projection (filters to externally-publishable events)
    ▼
Webhook Subscription delivery (HMAC-signed, retried with dead-letter, idempotent delivery ID)
    ▼
External App webhook endpoint
```

---

## 7. Capability Marketplace (Msika Apps)

`msika-apps-service` is the **governed catalogue** for apps, plugins, extensions, connectors, adapters, workflow packs, content packs, AI skills, and device integrations. It is **distinct** from:

- `msika-service` (national products & services registry / commerce catalog) — physical products and services.
- `msika-flow-service` (commerce orchestration) — orders, fulfilment, vendor operations.

Responsibility split:

| Service | Owns |
|---------|------|
| `msika-apps-service` (Capability Marketplace, "Msika Apps") | Marketplace item registry, publisher registry, activation requests, installations, configuration state, suspension, version updates, marketplace audit |
| `msika-service` | Product/service registry, catalogue ingestion/mapping, governance of physical items |
| `msika-flow-service` | Marketplace orchestration of commercial procurement and fulfilment |
| `integration-hub` (Integration Service governance) | External app registration, integration contracts, webhook subscriptions, event catalogue, S2S contracts |
| `developer-portal-service` | Developer client onboarding, API key issuance, certification |
| TSHEPO | Authorization decisions for activation, role-based marketplace visibility, scope enforcement on activated capabilities |
| Nompilo (via `guidance-service` + `llm-orchestration-service`) | Guided discovery, explanation, troubleshooting, role-aware marketplace recommendations |
| `audit-ledger-service` | Audit of marketplace activation/suspension events |

### 7.1 Visibility levels

A marketplace item has one of:

`PUBLIC`, `MOHCC_ONLY`, `FACILITY_ONLY`, `PROGRAMME_ONLY`, `PROVINCE_ONLY`, `DISTRICT_ONLY`, `PRIVATE_SECTOR_ONLY`, `SANDBOX_ONLY`, `DEVELOPER_PREVIEW`, `DEPRECATED`, `SUSPENDED`.

### 7.2 Activation lifecycle

```
LISTED → REQUESTED → IN_REVIEW → APPROVED → INSTALLED → CONFIGURED → ACTIVE
                                       └→ REJECTED
                                                        └→ SUSPENDED → REINSTATED | DEPRECATED
```

Each transition is recorded in the `marketplace_audit_events` table with actor, role, tenant, facility, reason, correlation id.

### 7.3 Activation gates

| Item type | Required approvals |
|-----------|-------------------|
| App | Data governance + Security + Tenant admin (+ Clinical safety if clinical-classified) |
| Extension | Data governance + Tenant admin (+ Clinical safety if clinical) |
| Plugin | Tenant admin (+ Clinical safety if clinical) |
| Connector | Data governance + Security + Tenant admin + (Commercial if paid) |
| Adapter | Security + Engineering owner |
| Workflow Pack | Clinical safety + Tenant admin |
| Content Pack | Editorial + Clinical safety (if clinical) |
| AI Skill | Data governance + Clinical safety + AI governance + Tenant admin |
| Device Integration | Security + Clinical safety + Tenant admin |

---

## 8. Canonical integration ports (anti-vendor-lock-in)

The experience layer calls **canonical Impilo services**. Vendor adapters live behind canonical ports.

See `docs/architecture/CANONICAL_INTEGRATION_PORTS.md` for the full catalogue. Summary:

| Port | Adapters (registered as marketplace items of type ADAPTER) |
|------|------------------------------------------------------------|
| `ImagingIntegrationPort` | DicomWebPacsAdapter, Hl7RisAdapter, VendorSpecificPacsAdapter, NativeOrthancAdapter |
| `LaboratoryIntegrationPort` | FhirLimsAdapter, Hl7v2LimsAdapter, VendorSpecificLimsAdapter, NativeOrosAdapter |
| `LogisticsIntegrationPort` | ExternalElmisAdapter, StockEventAdapter, NativeInventoryAdapter |
| `TelemedicineIntegrationPort` | ExternalVideoProviderAdapter, ExternalMessagingProviderAdapter, NativeTelemedicineAdapter |
| `OmnichannelIntegrationPort` | SmsProviderAdapter, WhatsAppProviderAdapter, EmailProviderAdapter, IvrProviderAdapter, PushNotificationAdapter, NativeNotificationAdapter |
| `PaymentIntegrationPort` | MobileMoneyAdapter (EcoCash, OneMoney, …), BankAdapter, CardPaymentAdapter, ClaimsSwitchAdapter, NativeMusheXAdapter |
| `MapsIntegrationPort` | DefaultMapProviderAdapter, OpenStreetMapAdapter, GoogleMapsAdapter, MapboxAdapter, NativeNdilaAdapter |
| `AIProviderPort` | GeminiAdapter, OpenAIAdapter, AnthropicAdapter, DeepSeekAdapter, LocalModelAdapter |
| `DeviceIntegrationPort` | BiometricAdapter, GlucometerAdapter, BPDeviceAdapter, ColdChainSensorAdapter, VehicleTrackerAdapter, DroneTelemetryAdapter, HealthConnectAdapter |

---

## 9. Trust contracts: required headers per caller class

See `libs/tech-companion/.../CompanionHeaders.java` for the canonical constants.

### 9.1 Internal sovereign service-to-service

Mandatory: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`, `Authorization`, `X-Service-Id`, `X-Service-Version`, `X-Request-Source`, `X-Purpose-Of-Use`.

Conditional: `X-Actor-ID`, `X-Actor-Type`, `X-Provider-ID` (when human-initiated); `X-Facility-ID`, `X-Workspace-ID`, `X-Shift-ID` (when operational context applies); `Idempotency-Key` (for state-changing requests); `X-External-App-Id` (when the call originated from an external app and is now propagating internally).

### 9.2 External app → Health OS

Mandatory: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`, `Authorization` (OAuth2 client-credentials or mTLS cert), `X-External-App-Id`, `X-Integration-Type`, `X-Integration-Version`, `X-Purpose-Of-Use`, `Idempotency-Key`, `X-Request-Signature`.

Conditional: `X-Subject-ID` (subject of the action), `X-Access-Mode=EXTERNAL`, `X-Device-Fingerprint` (for end-user-mediated flows).

### 9.3 Experience layer (web/mobile) → BFF

Per `docs/doctrine/health-os-doctrine.md` §8. The BFF rewrites and forwards to internal services with the S2S pattern, adding `X-Service-Id=experience-bff`, `X-Service-Version=…`, `X-Request-Source=HUMAN`.

---

## 10. Plugin and AI Skill runtime safety

**Frontend plugins** registered through the Capability Marketplace MUST:

1. Be loaded into an approved extension boundary (the **ExtensionFrame** primitive in `one-ui-shell`).
2. Have no access to session tokens. The shell injects a per-plugin **constrained context** (scoped Health OS headers proxied through the BFF only).
3. Not perform direct cross-origin requests; all outbound calls go via `/internal/v1/plugins/{pluginId}/proxy/*` which the BFF brokers against the plugin's approved scopes.
4. Not inject scripts into unrelated UI state. CSP enforces `script-src 'self'` plus per-plugin nonce.
5. Be auditable per call.

**Backend plugins** (a future runtime; currently scaffolded through `rules-service` and `forms-service`) MUST:

1. Run under a derived service identity tied to the plugin manifest.
2. Use only approved APIs declared in their manifest.
3. Be sandboxed by namespace + RBAC.
4. Be versioned, rollback-able, and audited.

**AI skills** MUST:

1. Declare required data access, allowed actions, role scope, and purpose binding in their manifest.
2. Be invokable only by an actor with the matching role + purpose + tenant.
3. Be logged with model id, skill id, input hash, output hash, correlation id, decision context.
4. Require explicit user confirmation for high-impact actions (declared in manifest as `requiresConfirmation: true`).

---

## 11. Error and failure UX

External integration failures are first-class citizens. The web shell and mobile apps render polite, role-aware messages — never raw stack traces. See `docs/developer/INTEGRATION_GUIDE.md` § "User-facing failure states".

Examples:

- The connected laboratory system has not yet returned this result.
- This payment is still being confirmed by the payment provider.
- This telemedicine service is temporarily unavailable. Please try again or use another approved channel.
- This external application is not authorised to access this record for the selected purpose.
- This app is available in the marketplace but has not yet been activated for your facility.
- This connector has been suspended pending security review.

---

## 12. Sovereignty guarantees

The doctrine maintains the following non-negotiable guarantees:

1. **No external app accesses internal databases directly.**
2. **No external app subscribes to internal Kafka topics directly.**
3. **No external app bypasses Envoy, TSHEPO, or audit.**
4. **No internal event is externally published unless explicitly classified `EXTERNALLY_PUBLISHABLE`.**
5. **No marketplace item is activated without an authorised approver, recorded in `marketplace_audit_events`.**
6. **No frontend plugin reads tokens or accesses cross-tenant data.**
7. **No vendor-specific protocol leaks into the experience layer; everything goes through a canonical port.**
8. **No integration contract is informal.** Every external partner has a stored, versioned, signed contract record.
9. **No marketplace card is shown that is not backed by a real, queryable marketplace item.**

---

## 13. Where this doctrine is implemented

| Doctrine area | Implemented in |
|---------------|----------------|
| Headers | `libs/tech-companion/.../CompanionHeaders.java` + `contracts/health-os-identifiers.ts` + `contracts/service-to-service-trust.ts` |
| Capability taxonomy (TS types) | `contracts/health-os-extensibility.ts` |
| Manifest schemas | `contracts/schemas/*-manifest.schema.json` |
| Integration contract / S2S contract / webhook / event catalogue REST | `integration-hub` (`/internal/v1/external-apps`, `/internal/v1/integration-contracts`, `/internal/v1/s2s-contracts`, `/internal/v1/webhook-subscriptions`, `/internal/v1/event-catalogue`) |
| Capability marketplace (Msika Apps) REST | `msika-apps-service` (`/internal/v1/marketplace/apps/**`) |
| BFF surfacing | `experience-bff` (`LauncherController`, `CapabilityMarketplaceController`, `IntegrationRegistryController`, `EventCatalogueController`, `ApiCatalogueController`) |
| Web UI | `ui/one-ui-shell/src/app/marketplace/apps/**`, `ui/one-ui-shell/src/app/admin/integration-registry/`, `ui/one-ui-shell/src/app/developer/**` |
| Mobile parity | `apps/mobile/packages/mobile-launcher/`, `apps/mobile/packages/mobile-marketplace/` |
| Adapter pattern | `docs/architecture/CANONICAL_INTEGRATION_PORTS.md` + canonical ports registered as marketplace items |

---

## 14. What this doctrine deliberately does NOT do

- It does not deprecate `msika-service` or `msika-flow-service`. They remain owners of product/service registry and commercial fulfilment.
- It does not replace `integration-hub`'s runtime routing. It **adds** a governance layer to it.
- It does not require deletion of the existing static `SHELL_APPS` registry — that registry stays as the **bootstrap / offline fallback**, and the launcher merges it with the **backend installed-apps state**.
- It does not require renaming existing services that happen to be called by user-friendly names (e.g. `learning-service` aka "Fundo", `costing-engine-service` aka "COSTA"). Naming alignment is a separate, smaller convergence track.

---

## 15. Compliance and audit

For audit and regulatory review, the Integration Registry exposes:

- A full, queryable list of registered external applications, their owners, contacts, environments, status, and current credential expiry.
- A full, queryable list of integration contracts with version history, scope, purpose, signature method.
- A full, queryable list of webhook subscriptions with delivery success rate and last error.
- A full, queryable list of S2S contracts with last verification.
- The marketplace audit log: every activation, suspension, configuration change.

All of these can be exported by an authorised administrator through the Integration Registry admin UI under `/admin/integration-registry`.
