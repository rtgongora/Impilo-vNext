# Experience Plane Dependency Map

Date: 2026-05-14

## Core Flow Topology

`UI (one-ui-shell / experience-ui)` -> `experience-bff` -> sovereign and domain services.

## Key Wiring Chains

| Experience capability | UI route(s) | BFF route(s) | Primary dependency services | Plane dependency notes |
|---|---|---|---|---|
| Auth/session entry | `/auth/*`, `/home`, context bars | `/internal/v1/auth/*`, `/internal/v1/profile/*` | Keycloak + trust services | Trust dependency required (authn/authz context). |
| Facility/workspace selection | `/facility`, `/workspace/*`, `/organization-admin/*` | `/internal/v1/facilities*`, `/internal/v1/workspaces/*` | `tuso-service` | Registry dependency; now fail-close for key facility paths. |
| Provider activation | provider-facing auth/work routes | `/internal/v1/identity/providers` | `varapi-service` | Registry dependency; placeholder fallback removed in this pass. |
| Registry geo/locality | registry and intake surfaces | `/internal/v1/registry/geo/*`, `/internal/v1/registry/localities/*` | `tuso-service` | Registry dependency; explicit upstream failure behavior enforced. |
| Mobile provider notices | mobile provider surfaces | `/internal/v1/mobile/provider/notices` | `varapi-service` | Registry dependency; previously empty stub now backend-wired. |
| Mobile provider reports | mobile provider report routes | `/internal/v1/mobile/provider/reports*` | `reporting-service` | Data/reporting dependency; previously stubbed payloads removed. |
| Staffing and swaps | scheduling/operations views | `/internal/v1/staffing/*` | `tuso-service` | Registry/ops dependency; local fake persistence removed. |
| Clinical tools and chart | `/clinical*`, `/ehr/*` | `/internal/v1/clinical*`, `/internal/v1/clinical-tools/*` and other clinical routes | clinical services via BFF clients | Clinical plane dependency, route set large and mixed readiness. |
| Finance/commerce orchestration | `/finance/*`, `/marketplace/*`, `/wallet/*` | `/internal/v1/finance/*`, `/internal/v1/commerce/*`, `/internal/v1/product-registry/*` | mushex/costa/msika/product-registry and related | Enterprise + Registry dependencies; finance BFF read routes now fail-close on COSTA failures. |
| Coverage operations | `/coverage/*` | `/internal/v1/coverage/*` | `coverage-service` | Enterprise/insurance dependency; list routes now fail-close on coverage upstream errors. |
| Integration-hub admin visibility | `/admin/integration-status` | `/internal/v1/integration-hub/*` | `integration-hub` | Integration plane dependency; list routes/deadletters/templates now fail-close with typed upstream errors. |
| Mobile clinical summaries/tools | mobile provider experiences via app | `/internal/v1/mobile/provider/labs*`, `/internal/v1/mobile/provider/schedule`, `/internal/v1/mobile/provider/telemedicine/*`, `/internal/v1/mobile/provider/prescriptions*` | `oros-service`, `tuso-service`, `pct-service`, `pharmacy-service` | Selected mobile clinical routes now fail-close; prescription write/cancel intentionally return explicit not-implemented until backend endpoints exist. |

## Context Propagation Expectations

Experience flows require propagation of:

- `X-Tenant-Id`
- `X-Request-Id`
- `X-Correlation-Id`
- actor and purpose/trust context headers where route requires trust-gated behavior.

## Current Dependency Risks

- Long-tail BFF routes still need portfolio-wide error-envelope/header convergence checks.
- Cross-plane operational evidence is uneven across large route portfolios (notably clinical/public-health/admin subdomains).
