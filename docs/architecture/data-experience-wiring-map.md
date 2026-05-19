# Data Experience Wiring Map

| UI route/component | BFF route | Backend owner | Wiring state | Notes |
|---|---|---|---|---|
| `ui/experience` public health surveillance tab | `/internal/v1/public-health/signals|cases|counters|alerts` | `surveillance-service` | wired | live reads, typed fail-close on upstream failure |
| `ui/experience` public health campaigns tab | `/internal/v1/public-health/campaigns*` | `campaigns-service` | wired | create/list/get/dispatch/close proxied |
| `ui/experience` weekly IDSR section | `/internal/v1/public-health/weekly-idsr` | `surveillance-service` (via counters aggregate) | wired in this pass | bounded aggregate projection with typed `502` fail-close |
| `ui/experience` outbreaks write intent | `/internal/v1/public-health/outbreaks` | `surveillance-service` (signal create) | wired in this pass | bounded write via signal primitive; typed `502` on upstream failure |
| `ui/experience` field operations write intent | `/internal/v1/public-health/field-operations` | `surveillance-service` (ingest) | wired in this pass | bounded write via ingest primitive; typed `502` on upstream failure |
| `ui/experience` AI governance model pages | `/internal/v1/ai/*` | `ai-model-registry-service` | wired in this pass | new BFF AI controller |
| `ui/experience` report job pages | `/internal/v1/reports/generate`, `/internal/v1/reports/{id}`, `/internal/v1/admin/reports/jobs` | `reporting-service` | wired | fail-close applied for upstream errors |
| Mobile provider governance summary/incidents/notifications | `/internal/v1/mobile/provider/governance/*` | `data-governance-service` + `notification-service` | wired | now fail-close instead of silent fallback |
| `ui/one-ui-shell` AI governance hooks | `/internal/v1/ai/*` | `ai-model-registry-service` | wired | parity endpoint family now exists on BFF |
