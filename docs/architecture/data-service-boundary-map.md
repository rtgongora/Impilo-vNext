# Data Service Boundary Map

## Data Ingestion (`data-ingestion-service`)

- Owns inbound data capture, staging, source tracking, ingest validation, ingest error recording.
- Does not own clinical identity or encounter SoR.

## Data Pipeline (`data-pipeline-service`)

- Owns pipeline run execution, transformation workflows, watermark/replay mechanics, materialization triggers, run status.
- Does not bypass governance and access controls.

## Data Governance (`data-governance-service`)

- Owns dataset classification, quality policy metadata, retention metadata, stewardship decisions, governance snapshots.
- Does not act as clinical or registry SoR.

## Data Access Governance (`data-access-governance-service`)

- Owns dataset/data-product access request lifecycle, grant/deny/revoke/expiry semantics, sharing policies, governance decision references.
- Must not bypass TSHEPO authorization/audit and purpose-of-use checks.

## National Data Repository (`ndr-service` + `national-data-repository-service`)

- Owns curated aggregate/analytic national datasets and governed query paths.
- Must not become clinical encounter SoR.
- Consolidation note: current split ownership is a blocker; single runtime owner required.

## Reporting (`reporting-service`)

- Owns report definition, run, schedule, export metadata, tenant run history.
- Does not own underlying source datasets.

## Data Warehouse (`data-warehouse-service`)

- Owns gold dataset materialization and historical analytics structures.
- Must enforce tenant scoping and governed access for query endpoints.

## Surveillance (`surveillance-service`)

- Owns public-health event monitoring, signal/case/alert/counter operations, escalation metadata.
- Does not own patient identity or individual clinical SoR.

## Campaigns (`campaigns-service`)

- Owns campaign planning, enrollment/dispatch/closure lifecycle, campaign outcome monitoring.
- Does not own clinical follow-up encounter records.

## AI Model Registry (`ai-model-registry-service`)

- Owns model metadata, versioning, approval/withdrawal state, inference/drift governance metadata.
- Does not provide autonomous clinical decision authority.

## Experience BFF (`experience-bff`)

- Owns UI orchestration only: proxy, validation, typed fail-close, response normalization.
- Must not synthesize success for unavailable data/public-health/reporting capabilities.
