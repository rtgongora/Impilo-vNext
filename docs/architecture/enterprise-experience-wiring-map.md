# Enterprise Experience Wiring Map

Date: 2026-05-15

| UI route group | BFF route group | Enterprise backend | Status | Notes |
|---|---|---|---|---|
| `ui/experience/src/app/finance/*` | `/internal/v1/finance/*` | `costing-engine-service`, `mushex-service` | partial | high-value routes fail-close; long-tail parity still pending |
| `ui/experience/src/app/coverage/*` | `/internal/v1/coverage/*` | `coverage-service` | partial | list/read paths hardened; deeper mutation parity pending |
| `ui/experience/src/app/marketplace/*` | `/internal/v1/marketplace/*`, `/internal/v1/commerce/*` | `msika-flow-service`, `msika-service` | partial | order-create synthetic fallback removed in this pass |
| `ui/experience/src/app/erp/procurement/*` | `/internal/v1/erp/procurement/*` | `procurement-service` | partial | wired but UX depth/test depth remains limited |
| `ui/experience/src/app/erp/hr/*` | `/internal/v1/erp/hr/*` | `hr-payroll-service` | partial | wired but workflow depth remains limited |
| mobile provider billing routes | `/internal/v1/mobile/provider/billing/*` | intended COSTA/enterprise linkage | explicit unavailable | now returns typed `501 BILLING_ROUTE_UNAVAILABLE` (no fake success) |

## Route Registry Drift (Tracked)

- `ui/one-ui-shell` includes enterprise/finance route registrations that are not fully mirrored in `ui/experience`.
- Some route registry entries have no matching page implementation and remain tracked as experience-layer backlog.
