# Service — API client coverage (Experience BFF + UI)

| Upstream | Base URL (env) | BFF client / controller | Typed UI hook / caller | Notes |
|----------|----------------|-------------------------|-------------------------|--------|
| COSTA | `COSTA_BASE_URL` (8101) | `CostaServiceClient` | `useCostaTariffLists` → `apiClient` GET `/internal/v1/finance/costa-intel/tariff-lists` | **Tariff library** page now uses this path |
| COSTA | same | `FinanceController` | `apiClient` GET `/internal/v1/finance/tariffs` | Legacy `TariffEntity` list |
| PCT / VITO / Mvumo | per `application.yml` | dedicated clients | `api-client`, feature hooks | See architecture route maps |
| mushex | `MUSHEX_BASE_URL` | BFF mushex controllers | finance settlement pages | — |

**Recommendation:** OpenAPI / Zod for BFF `internal/v1` responses where UI complexity grows; today many endpoints return `JsonNode` or resource maps from `FinanceController`.
