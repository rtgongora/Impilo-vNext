# BFF/API Wiring Audit

## End-to-End Trace Matrix (sample high-value flows)

| Feature | Web caller | Mobile caller | BFF endpoint | Backend/service | Status |
|---|---|---|---|---|---|
| Core Transaction view | Fixture pages (no live hook) | Missing dedicated journey | `/internal/v1/core-transactions/*` | Experience composition + sovereign services | NOT_WIRED_ANYWHERE (frontend) |
| Nompilo command | Partial command UI | Limited parity | `/internal/v1/core-transactions/{id}/nompilo/command` | Composition service | PARTIAL |
| Queue triage | `queue/triage` + queue hooks | Provider queue flows | `/internal/v1/queue/*` and mobile provider queue paths | Queue services | WIRED |
| Telemedicine | web telemedicine pages/hooks | mobile telemedicine screens/services | `/internal/v1/teleconsult/*` and mobile telemedicine namespace | Telemedicine services | PARTIAL_PARITY |
| Coverage claims | coverage hooks/pages | citizen coverage service | `/internal/v1/coverage/*` | Coverage service | WIRED_PARTIAL |
| Reporting | report hooks/pages | mobile reports screen/service | `/internal/v1/reports/*` | Reporting services | WIRED_PARTIAL |
| Support | support pages + support console | mobile support surfaces | `/internal/v1/support/*` + mobile support | Support service | WIRED_PARTIAL |
| Workflow/dispatch | none | none | `/internal/v1/workflows/*`, `/internal/v1/dispatch/*` | workflow-service/dispatch-service | BACKEND_ONLY |

## Endpoint Integrity Risks

1. Core-transaction BFF routes exist but are not consumed by doctrine web pages.
2. Provider mobile messaging path mismatch risk (`/channels/messages` vs mobile provider messaging namespace).
3. Some controllers return empty data fallbacks on upstream failure; can mask real errors.
