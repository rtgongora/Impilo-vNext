# Experience BFF — Phase C domain mapping

**Status:** Phase **C** is **complete**. Phase **D** (Experience UI) is **complete** for the slices in the agent-led roadmap (search, public-health honesty, admin integration hub, guard alignment); see [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md).

Links **typed HTTP clients**, **`impilo.services` / other config**, **downstream Maven modules**, and **OpenAPI contracts** under [`contracts/openapi/`](../../contracts/openapi/). Use with:

- [`experience-bff-downstream-route-map.md`](./experience-bff-downstream-route-map.md) — base URL table
- [`experience-bff-internal-routes.md`](./experience-bff-internal-routes.md) — generated `/internal/v1` controller index

---

## 1. Typed clients → config → service → contract

| Client (bean) | Config key | Default (localhost) | Downstream module | OpenAPI (when present) |
|---------------|------------|--------------------|-------------------|-------------------------|
| `PctServiceClient` | `impilo.services.pct-base-url` | 8088 | `pct-service` | `pct.openapi.yaml` |
| `OrosServiceClient` | `impilo.services.oros-base-url` | 8089 | `oros-service` | `oros.openapi.yaml` |
| `PharmacyServiceClient` | `impilo.services.pharmacy-base-url` | 8096 | `pharmacy-service` | `pharmacy.openapi.yaml` |
| `ButanoServiceClient` | `impilo.services.butano-base-url` | 8090 | `butano-service` (FHIR host) | `butano.custom.openapi.yaml` (see also BUTANO FHIR module) |
| `MsikaServiceClient` | `impilo.services.msika-base-url` | 8086 | `msika-service` | `msika-core.openapi.yaml` |
| `MsikaFlowServiceClient` | `impilo.services.msika-flow-base-url` | 8100 | `msika-flow-service` | `msika-flow.openapi.yaml` |
| `MushexServiceClient` | `impilo.services.mushex-base-url` | 8102 | `mushex-service` | `mushex.openapi.yaml` |
| `VitoServiceClient` | `impilo.services.vito-base-url` | 8082 | `vito-service` | `vito.openapi.yaml` |
| `TusoServiceClient` | `impilo.services.tuso-base-url` | 8084 | `tuso-service` | `tuso.openapi.yaml` |
| `VarapiServiceClient` | `impilo.services.varapi-base-url` | 8083 | `varapi-service` | `varapi.openapi.yaml` |
| `DocumentServiceClient` | `impilo.services.document-store-base-url` | 8093 | `document-service` | `document-store.openapi.yaml` |
| `CostaServiceClient` | `impilo.services.costa-base-url` | 8101 | `costing-engine-service` | `costa.openapi.yaml` |
| `CoverageServiceClient` | `impilo.services.coverage-base-url` | 8140 | `coverage-service` | `coverage.openapi.yaml` |
| `CredentialServiceClient` | `impilo.services.credential-base-url` | 8094 | `credential-verification-service` | `credential-verification.openapi.yaml` |
| `ExtensionServiceClient` | `forms-base-url`, `rules-base-url` | 8240 / 8241 | `forms-service`, `rules-service` | `forms.openapi.yaml`, `rules.openapi.yaml` |
| `FhirGatewayServiceClient` | `fhir-gateway-base-url` | 8091 | `fhir-gateway-service` | `fhir-gateway.openapi.yaml` |
| `FhirPublisher` | `impilo.services.fhir-base-url` | 8090/fhir | BUTANO FHIR surface | `butano.custom.openapi.yaml` |
| `SearchServiceClient` | `search-base-url` | 8230 | `search-service` | `search.openapi.yaml` |
| `IntegrationHubServiceClient` | `integration-hub-base-url` | 8110 | `integration-hub` | `integration-hub.openapi.yaml` |
| `GuidanceServiceClient` | `guidance-base-url` | 8260 | `guidance-service` | `guidance.openapi.yaml` |
| `ClinicalKnowledgePlatformClient` | `impilo.clinical-platform.base-url` | 8270 | `clinical-knowledge-platform-service` | `clinical-knowledge-platform.openapi.yaml` |
| *(HTTP proxy, no named `*Client` bean)* | `impilo.services.wellness-base-url` | 8161 | `wellness-service` | `wellness.openapi.yaml` |

`RestTemplate` + `ServiceEndpoints` (no dedicated bean): **`PublicHealthController`** → `surveillance-base-url`, `campaigns-base-url`, `indawo-base-url`; **`AccessChannelsController`**, **`ClinicalToolsController`**, **`AiGovernanceController`**, **`MobileGovernanceController`** → `landela-base-url`, `data-governance-base-url`, etc. (see each controller).

---

## 2. Representative BFF prefixes → downstream

These are **aggregates** (one prefix may fan out to several downstream calls). Full detail lives in controller sources.

| BFF prefix (stem) | Typical client(s) / pattern | Notes |
|-------------------|----------------------------|--------|
| `/internal/v1/encounters`, `/queue`, `/triage`, mobile encounters | `PctServiceClient`, `CostaServiceClient` | Journey + costing |
| `/internal/v1/lab-orders`, mobile labs | `OrosServiceClient` | Lab orders |
| `/internal/v1/clinical` (assistant, etc.) | `ClinicalKnowledgePlatformClient` | CKP |
| `/internal/v1/clinical/curation`, `/clinical/source` | `ClinicalKnowledgePlatformClient` | Curation + ingestion |
| `/internal/v1/clinical-documents`, documents | `DocumentServiceClient` | Document Store |
| `/internal/v1/extensions` | `ExtensionServiceClient` | Forms + rules |
| `/internal/v1/guidance` | `GuidanceServiceClient` | §13 guidance |
| `/internal/v1/fhir` | `FhirGatewayServiceClient` | Gateway control / forward |
| `/internal/v1/summary` | `ButanoServiceClient` | IPS / summary proxies |
| `/internal/v1/identity`, `/registry`, `/identity/providers` | `VitoServiceClient`, `VarapiServiceClient` | Identity + provider registry |
| `/internal/v1/appointments`, `/shifts`, scheduling | `TusoServiceClient` | Facility / schedule context |
| `/internal/v1/public-health` | `RestTemplate` + surveillance / campaigns / indawo URLs | No single `*Client` |
| `/internal/v1/finance/*`, payer ops | `CostaServiceClient`, `MushexServiceClient` | Billing + claims rail |
| `/internal/v1/coverage` | `CoverageServiceClient` | Payer eligibility |
| `/internal/v1/commerce/*`, marketplace | `MsikaFlowServiceClient` | Marketplace flow |
| `/internal/v1/msika`, `/product-registry` | `MsikaServiceClient` | Product / catalogue |
| `/internal/v1/credentials` | `CredentialServiceClient` | Credential verification |
| `/internal/v1/search` | `SearchServiceClient` | search-service (`q`, optional `entityType`, paging) — UI: `useKnowledgeSearch` |
| `/internal/v1/pharmacy/upstream/**` | `PharmacyServiceClient` | Sovereign dispense orders / worklists |
| `/internal/v1/integration-hub/**` | `IntegrationHubServiceClient` | Routes, dispatch, dead letters, mapping templates — **BFF:** `hasAnyRole(ADMIN_ROLES)` only (not general authenticated) |

---

## 3. BFF-local surfaces (no downstream typed client)

Some controllers use **BFF PostgreSQL** (`JdbcTemplate`, JPA repositories) only — e.g. **`PatientController`**, **`InventoryController`** under `/internal/v1/patients`, `/inventory`. **`PharmacyController`** is hybrid: prescriptions and local dispense stay on the BFF DB; **`/internal/v1/pharmacy/upstream/**`** delegates to `PharmacyServiceClient`.

**Citizen wellness, health wallet, and Health Connect** (`/internal/v1/mobile/citizen/wellness/**`, `/wallet`, `/internal/v1/wellness/connect/**`) are **no longer BFF-local**: the BFF **`WellnessServiceProxyController`** forwards (same paths + trust headers) to **`wellness-service`** (port **8161**, `impilo.services.wellness-base-url`). Persistence remains on the Experience BFF PostgreSQL database by default (`DB_NAME=experience_bff`) until a dedicated wellness database is introduced in ops.

---

## 4. Phase C follow-ups

No open items from the original Phase C gap list. **Phase D** (Experience shell) is **closed for the agent-led roadmap slices** (search, public-health honesty, admin integration hub UI, guard alignment); see [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md). Further Experience polish is tracked under **Phase E/F** or ad-hoc slices.

---

## Related

- Kafka topic inventory (Phase E): [`kafka-event-catalog.md`](./kafka-event-catalog.md)
- Registry: [`docs/registry/services-registry.yaml`](../registry/services-registry.yaml)
- Completeness mapper: [`scripts/completeness/generate-completeness-report.mjs`](../../scripts/completeness/generate-completeness-report.mjs) (`OPENAPI_BY_MODULE`, `BFF_CLIENT_BY_MODULE`)
