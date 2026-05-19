# Canonical Integration Ports

> Implements `docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md` §8.
> Adapter registration metadata: `contracts/schemas/adapter-manifest.schema.json`.

## 1. Why canonical ports

The Impilo experience layer (web, mobile, BFF, sovereign services) MUST call **canonical Impilo services**, not external vendor systems directly. Vendor implementations sit behind canonical ports as **adapters**.

This is the **single architectural guarantee** that lets us swap external vendors without rewriting clinical or operational UX.

## 2. The nine canonical ports

| Port | Owner service | Default native adapter | External adapters (examples) |
|------|---------------|-----------------------|-----------------------------|
| `ImagingIntegrationPort` | `pacs-adapter-service` | `NativeOrthancAdapter` | `DicomWebPacsAdapter`, `Hl7v2RisAdapter`, `VendorSpecificPacsAdapter` |
| `LaboratoryIntegrationPort` | `oros-service` (+ `connector-fhir-adapter`) | `NativeOrosAdapter` | `FhirLimsAdapter`, `Hl7v2LimsAdapter`, `VendorSpecificLimsAdapter` |
| `LogisticsIntegrationPort` | `inventory-service` (+ `pharmacy-elmis-adapter`, `inventory-elmis-adapter`) | `NativeInventoryAdapter` | `ExternalElmisAdapter`, `StockEventAdapter` |
| `TelemedicineIntegrationPort` | `scheduling-service` + `channels-service` | `NativeTelemedicineAdapter` | `ExternalVideoProviderAdapter`, `ExternalMessagingProviderAdapter` |
| `OmnichannelIntegrationPort` | `notification-service` + `channels-service` | `NativePushAdapter` | `SmsProviderAdapter`, `WhatsAppProviderAdapter`, `EmailProviderAdapter`, `IvrProviderAdapter` |
| `PaymentIntegrationPort` | `mushex-service` | `NativeMusheXAdapter` | `EcoCashAdapter`, `OneMoneyAdapter`, `BankAdapter`, `CardPaymentAdapter`, `ClaimsSwitchAdapter` |
| `MapsIntegrationPort` | `ndila-service` | `NativeNdilaAdapter` | `OpenStreetMapAdapter`, `GoogleMapsAdapter`, `MapboxAdapter` |
| `AIProviderPort` | `llm-orchestration-service` | `LocalModelAdapter` | `GeminiAdapter`, `OpenAIAdapter`, `AnthropicAdapter`, `DeepSeekAdapter` |
| `DeviceIntegrationPort` | `iot-ingestion-service` (+ `asset-registry-service`) | `NativeHealthConnectAdapter` | `BiometricAdapter`, `GlucometerAdapter`, `BPDeviceAdapter`, `ColdChainSensorAdapter`, `VehicleTrackerAdapter`, `DroneTelemetryAdapter` |

## 3. Adapter registration

Each adapter is registered as a `MarketplaceItem` of `type=ADAPTER` in `msika-apps-service`. The `AdapterManifest` (`contracts/schemas/adapter-manifest.schema.json`) MUST declare:

- `canonicalPort` — the port the adapter implements
- `adapterImplementationClass` — fully qualified class in the owner service
- `vendorName`, `vendorVersion`
- `defaultForPort` — only one adapter may be marked default per (tenant, port) pair

At runtime, the owner service consults the activated installation of the adapter (per tenant + facility scope) to select the implementation.

## 4. Required port behaviours

Every canonical port MUST:

1. Define a **stable Java interface** (the port) in the owner service's `port` package.
2. Define a **stable DTO model** (the canonical data model) under `contracts/openapi/` and `contracts/`.
3. Provide a `NativeXxxAdapter` that uses the Impilo-internal capability, so the port is functional out of the box without any external vendor activation.
4. Implement **adapter resolution by tenant + facility** — an adapter activated only in District A does not become the default in District B.
5. Implement **graceful degradation** — if no adapter is healthy, return a `ServiceUnavailable` polite failure (see `docs/developer/INTEGRATION_GUIDE.md` § failure UX).
6. Emit `port.adapter.selected` and `port.adapter.failure` internal events for observability.

## 5. Doctrine: the experience layer never calls a vendor

| Bad | Good |
|-----|------|
| `apiClient.get('/external/ecocash/...')` from the BFF | `apiClient.post('/internal/v1/payments/intents', ...)` — MusheX selects the EcoCash adapter |
| `apiClient.get('/external/google-maps/route?...')` from the web | `apiClient.get('/internal/v1/ndila/routes?...')` — Ndila selects the active maps adapter |
| Citizen app talks to a vendor SDK directly for video calls | Citizen app calls `/internal/v1/mobile/citizen/telehealth/sessions/{id}/join`; channels-service brokers via the selected telemedicine adapter |

## 6. Adapter lifecycle

| Stage | Action |
|-------|--------|
| Publish | Vendor or platform publishes an `AdapterManifest` to `msika-apps-service` |
| Approve | Security review + tenant admin approve the activation request |
| Configure | Tenant admin supplies credentials (stored in vault-kms; never logged) |
| Activate | The owner service registers the adapter via its adapter registry |
| Monitor | Owner service emits adapter health events; integration-hub exposes admin status at `/admin/integration-status` |
| Suspend | Adapter is suspended by security review or repeated failures; native adapter takes over (when present) |
| Update | New version of adapter requires either compatibility-checked upgrade or fresh activation |

## 7. Examples in the existing repo

- **`pacs-adapter-service`** is the canonical owner of `ImagingIntegrationPort`. It already encapsulates Orthanc plus connector hooks; turn these into adapter selection driven by manifests.
- **`notification-service`** + **`channels-service`** already act as the omnichannel anchor; new SMS/WhatsApp/Email providers should arrive as `ConnectorManifest` + corresponding `AdapterManifest`.
- **`ndila-service`** owns `MapsIntegrationPort`; current implementation is the native adapter. Marketplace activation of an external map provider adapter would be a configuration concern, not a code rewrite at the call site.

## 8. Not yet wired (tracked in remaining-work)

- The Java interface bundles per port — these are scheduled to land as part of the next adapter-port-codification track. Until then, owner services expose REST endpoints that follow the doctrine and use internal selection.
- The web/mobile experience already calls the canonical owner services in nearly all places; the small number of exceptions are tracked in `docs/registry/backend-to-frontend-wiring-map.md` and in the final report attached to this implementation.
