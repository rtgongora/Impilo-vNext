# Data, Intelligence & Public Health Plane

NDR, warehousing, analytics, surveillance, indicators, search and public health intelligence.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `ai-model-registry-service` | `ai-model-registry-service` | intelligence | Ai Model Registry canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `campaigns-service` | `campaigns-service` | public-health-campaigns | public-health campaign definitions, campaign outreach plans and schedules, campaign execution state and coverage metrics | must-not-own-individual-clinical-encounter-record, must-not-own-patient-identity-source-of-truth, must-not-bypass-data-governance-or-consent-policy, must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries |
| `data-access-governance-service` | `data-access-governance-service` | intelligence | Data Access Governance canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-governance-service` | `data-governance-service` | intelligence | Data Governance canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-ingestion-service` | `data-ingestion-service` | intelligence | Data Ingestion canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-pipeline-service` | `data-pipeline-service` | intelligence | Data Pipeline canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-warehouse-service` | `data-warehouse-service` | intelligence | Data Warehouse canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `national-data-repository-service` | `national-data-repository-service` | intelligence | National Data Repository canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `ndr-service` | `ndr-service` | intelligence | Ndr canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `reporting-service` | `reporting-service` | intelligence | Reporting canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `search-service` | `search-service` | intelligence | Search canonical records | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `surveillance-service` | `surveillance-service` | public-health-surveillance | public-health surveillance signals and case aggregates, surveillance alert definitions and epidemiological counters, notifiable event monitoring telemetry | must-not-own-individual-clinical-encounter-record, must-not-own-patient-identity-source-of-truth, must-not-bypass-data-governance-or-consent-policy, must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.