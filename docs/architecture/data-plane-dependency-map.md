# Data Plane Dependency Map

## Core Dependencies

| Data service | Upstream dependencies | Downstream consumers | Dependency risk |
|---|---|---|---|
| data-ingestion-service | integration feeds, producer services | data-pipeline-service | medium |
| data-pipeline-service | ingestion outputs, governance metadata | NDR/warehouse/reporting/surveillance/campaigns | medium |
| data-governance-service | TSHEPO, metadata stores | BFF governance routes, pipeline controls | medium |
| data-access-governance-service | TSHEPO authz/audit, policy metadata | dataset access/reporting/surveillance/campaigns | high |
| ndr-service | governance client, producer events | reporting/analytics/public-health | high (merge overlap) |
| national-data-repository-service | governance metadata, dataset catalog users | reporting/integration | high (merge overlap) |
| data-warehouse-service | pipeline materialization events | reporting/analytics | medium |
| reporting-service | warehouse/NDR queries, export policy | BFF report routes, UI | medium |
| surveillance-service | ingestion/policy and public-health feeds | BFF public-health routes | medium |
| campaigns-service | registry references + governance policy | BFF public-health routes | medium |
| ai-model-registry-service | TSHEPO + model ops inputs | BFF AI routes | medium |
| experience-bff (data routes) | all above + notification/indawo | UI shells/mobile | high (orchestration choke point) |

## Cross-Plane Dependencies

- Trust: mandatory for authz, purpose-of-use, auditing.
- Registry: mandatory reference integrity.
- Clinical/Enterprise: producer-only SoR relation (Data Plane consumes copies).
- Integration: adapter/feed ingress path.
- Experience: controlled presentation path (must fail-close on upstream failure).

## Consolidation Dependency

- NDR merge dependency: `ndr-service` and `national-data-repository-service` must converge to avoid duplicate Data Plane ownership and contract drift.
