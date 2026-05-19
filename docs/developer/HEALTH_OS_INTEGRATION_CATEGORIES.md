# Health OS — Integration Categories

This document defines the five governed classes of caller / participant
recognised by Impilo vNext as a sovereign Health Operating System.

> **Doctrine.** Sovereign at the core, modular at the edges, extensible by
> design, secure by default, and governed through trusted registries,
> consent, policy, contracts, audit and marketplace-based activation.

See also: `docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md`,
`contracts/health-os-extensibility.ts`,
`contracts/openapi/msika-apps.openapi.yaml`,
`contracts/openapi/integration-governance.openapi.yaml`,
`contracts/asyncapi/health-os-events.asyncapi.yaml`.

---

## 1. Internal service-to-service (S2S)

Calls between sovereign Impilo services (TSHEPO, VITO, VARAPI, TUSO, INDAWO,
BUTANO, ZIBO, MUSHEX, COSTA, FUNDO, MSIKA, MSIKA Flow, MSIKA APPS, NHUME, NDILA,
NOMPILO, Comms Hub, Integration Hub, Surveillance, Inpatient, PCT, Pharmacy,
Workflow, Audit, Observability, …).

| Required headers | Where |
|------------------|-------|
| `X-Tenant-Id`, `X-Correlation-Id`, `X-Service-Id`, `X-Service-Name`, `X-Service-Version`, `X-Actor-Id`, `X-Actor-Type`, `X-Purpose-Of-Use`, `X-Facility-Id`, `X-Workspace-Id`, `X-Shift-Id`, `X-Request-Source`, `X-Idempotency-Key` | Enforced by `libs/tech-companion` |

* Caller must be registered as a `ServiceToServiceContract` in
  `integration-hub`. Check at runtime with
  `GET /internal/v1/s2s-contracts/check?caller=&callee=&requestSource=`.
* `X-Request-Source` distinguishes `HUMAN`, `SYSTEM`, `SCHEDULED_JOB`,
  `BACKGROUND_WORKER`, `EVENT_CONSUMER`, `AI_ASSISTED`, `EXTERNAL_APP`.
* Internal services subscribe to Kafka topics directly; **internal-only**
  events MUST NOT leak to external apps.

## 2. Experience layer (BFF + UI shells)

`experience-bff` is the only path through which the web shell, provider app,
citizen app and admin dashboards may reach sovereign services. The BFF:

* propagates the trust context onward (S2SHeaderEnricher);
* injects its own service identity (`X-Service-Id: experience-bff`);
* applies BFF-level rate limiting and audit;
* never returns secrets, signing keys, or unredacted internal-only payloads.

## 3. External institutional integrations (LIMS, PACS, eLMIS, payments, …)

External institutional callers MUST:

1. Register as an `ExternalApplication` in `integration-hub`.
2. Bind to one or more `IntegrationContract`s (defines allowed APIs,
   events, scopes, environments).
3. Authenticate with OAuth2 Client Credentials (or mTLS where mandated).
4. Send `X-External-App-Id`, `X-Integration-Type`, `X-Integration-Version`,
   `X-Tenant-Id`, `X-Correlation-Id`, `X-Purpose-Of-Use`, `X-Request-Signature`.
5. Receive webhooks signed with HMAC-SHA256 (see `WEBHOOK_GUIDE.md`).
6. Be auditable (every request is logged with `X-External-App-Id`).

## 4. Marketplace apps / plugins / extensions / connectors / adapters / packs / AI skills / device integrations

These are governed capabilities discovered, requested, approved and activated
through the **Health OS Capability Marketplace (Msika Apps)** at
`services/msika-apps-service`.

| Capability | Surface |
|------------|---------|
| `APP`             | Appears in the Health OS launcher |
| `EXTENSION`       | Adds a larger capability to a sovereign service or workflow |
| `PLUGIN`          | Small capability injected into a service or UI surface |
| `CONNECTOR`       | Wires Impilo to an external system (e.g. DHIS2 connector) |
| `ADAPTER`         | Vendor-specific implementation behind a canonical port |
| `WORKFLOW_PACK`   | Installs forms / queues / dashboards / permissions |
| `CONTENT_PACK`    | Installs approved content (templates, Fundo content, etc.) |
| `AI_SKILL`        | Extends Nompilo under TSHEPO policy |
| `DEVICE_INTEGRATION` | Links medical / biometric / IoT / cold-chain / vehicle devices |

Lifecycle: `LISTED → REQUESTED → IN_REVIEW → APPROVED → INSTALLED → CONFIGURED → ACTIVE → SUSPENDED → DEPRECATED`.

## 5. Citizen-mediated proxy (consent-based read)

Where a citizen (via the Impilo mobile app or wallet) authorises a third-party
to read a slice of their record (e.g. a private clinic, an insurer), the
caller is governed as a marketplace app **plus** a citizen-side consent
record managed by `tshepo-consent-service`. The proxy MUST attach the
`X-Citizen-Consent-Id` header on every call.

---

## Canonical ports (anti-vendor-lock-in)

All vendor-specific code goes through canonical Impilo ports defined in
`libs/shared-kernel-java/src/main/java/zw/gov/mohcc/impilo/sharedkernel/integration/ports/`:

* `ImagingIntegrationPort` (DICOMweb, HL7 RIS, native)
* `LaboratoryIntegrationPort` (HL7 v2, FHIR, native LIS)
* `LogisticsIntegrationPort` (eLMIS, native stock events)
* `TelemedicineIntegrationPort` (external video / messaging providers)
* `OmnichannelIntegrationPort` (SMS, WhatsApp, Email, IVR, push, native Comms Hub)
* `PaymentIntegrationPort` (mobile money, bank, card, claims switch, native MUSHEX)
* `MapsIntegrationPort` (OSM, Mapbox, native NDILA)
* `AIProviderPort` (OpenAI, Gemini, Anthropic, DeepSeek, local)
* `DeviceIntegrationPort` (biometric scanners, vitals devices, cold-chain, vehicles)

> The experience layer MUST call canonical Impilo services, not vendor
> adapters directly. Adapters live behind the port and are swappable.

---

## Quick reference: where to put new code

| You want to … | Put it here |
|---------------|-------------|
| Add a new sovereign capability | Service under `services/<name>-service/` |
| Register a partner LIMS for staging | `POST /internal/v1/external-apps` then `POST /internal/v1/integration-contracts` |
| Publish a new event topic for external apps | Add an `EventCatalogueEntry` with `classification: EXTERNALLY_PUBLISHABLE` in `integration-hub` |
| Offer a workflow pack across all districts | Publish to Msika Apps with `defaultVisibility: MOHCC_ONLY` and submit for approval |
| Add a Nompilo AI skill | Add manifest under `contracts/ai-skills/` + tool class under the owning service |
| Add a new vendor for an existing port (e.g. another LIMS vendor) | Implement the port in `services/<name>-adapter-service/`, register as a marketplace `ADAPTER`, bind via `IntegrationContract` |
