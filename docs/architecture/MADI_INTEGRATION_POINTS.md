# MADI Integration Points

## Upstream / peer services

| Service | Integration | Direction |
|---------|-------------|-----------|
| **VITO** | Resolve `person_cpid` for donors and transfusion patients | Read |
| **TUSO** | Facility and workspace context for drives and blood banks | Read |
| **Ndila** | `ndila_site_ref` for drives-near-me geospatial lookup | Read |
| **OROS** | Clinical order reference (`oros_order_ref`) on blood orders | Read/write link |
| **BUTANO** | Transfusion and reaction documentation to SHR (CPID only) | Write |
| **NHUME** | Blood product last-mile delivery where applicable | Read/write |
| **Inventory** | Commodity / cold-chain alignment for blood products | Read |

Integration adapters live in `services/madi-service/.../integration/`:

- `ButanoIntegration`
- `NdilaIntegration`
- `NhumeIntegration`
- `OrosIntegration`
- `InventoryIntegration`

## BFF surfaces

| Surface | Path prefix | Consumer |
|---------|-------------|----------|
| Domain direct | `/internal/v1/madi/*` | Experience BFF, ops consoles |
| Citizen mobile | `/internal/v1/mobile/citizen/madi/*` | `citizen-app` `madiService.ts` |
| Provider mobile | `/internal/v1/mobile/provider/madi/*` | `provider-app` `madiService.ts` |

Mobile clients **must not** call `/internal/v1/madi/*` directly — trust headers and actor scoping are composed at the BFF.

## Trust headers (mandatory)

Injected by `@impilo/mobile-api-client` and web `api-client.ts`:

- `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- `X-Actor-ID`, `X-Actor-Type`, `X-Purpose-Of-Use`
- `X-Facility-ID` (provider and facility-scoped donor ops)

## Envoy path

```
Client → Envoy (ext_authz) → TSHEPO → Experience BFF → madi-service
```

## Planned web routes

Web orchestration in `ui/one-ui-shell` will expose `/madi/*` operator and clinician workspaces — see [`MADI_FRONTEND_ROUTES.md`](./MADI_FRONTEND_ROUTES.md).
