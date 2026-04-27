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

## Related

- BFF **`/internal/v1` surface** (generated controller index): [`experience-bff-internal-routes.md`](./experience-bff-internal-routes.md) — `cd scripts/bff-routes && node list-bff-internal-routes.mjs`
- OpenAPI inventory: [`contracts/openapi/`](../../contracts/openapi/)
- Completeness dimensions: [`scripts/completeness/`](../../scripts/completeness/)
- Roadmap: [`docs/roadmaps/agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md)
