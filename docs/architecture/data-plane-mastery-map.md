# Data Plane Mastery Map

Status scale: `implemented` | `partial` | `missing` | `blocked` | `architecture-decision-required`

## Data Domains and Ownership

| Domain | Primary owner | Status | Notes |
|---|---|---|---|
| Ingestion | `data-ingestion-service` | partial | core service present; contract depth pending |
| Pipeline/transformation | `data-pipeline-service` | partial | orchestration present; canonical state convergence pending |
| Data governance | `data-governance-service` | implemented | governance APIs active; BFF fail-close improved |
| Data access governance | `data-access-governance-service` | partial | policy decisioning exists; cross-plane usage parity pending |
| NDR/national repository | `ndr-service` + `national-data-repository-service` | partial | runtime query ownership canonicalized to `ndr-service`; structural service merge remains pending |
| Data warehouse | `data-warehouse-service` | implemented | tenant-scoped query hardening completed this pass |
| Reporting | `reporting-service` | implemented | BFF fail-close/report run detail improved |
| Surveillance | `surveillance-service` | implemented | contracts updated to cover full route family |
| Campaigns | `campaigns-service` | implemented | contracts updated for `get/close/enroll/dispatch` |
| Public health operations | `experience-bff` + surveillance/campaigns/indawo | partial | read/write orchestration wired to dedicated surveillance lifecycle endpoints; long-tail dataset/test depth pending |
| AI/model registry | `ai-model-registry-service` | partial | upstream service exists, BFF orchestration added this pass |
| Analytics/dashboards | `experience-bff` + reporting/surveillance | partial | weekly aggregates and some dashboard composites unavailable |

## Cross-Plane Dependencies

- Trust: TSHEPO authz/audit headers required for all internal routes; MVUMO required when consent-gated access is needed.
- Registry: VITO/VARAPI/TUSO/ZIBO references are consumed for identity/facility/provider/terminology linkages only.
- Clinical: PCT/BUTANO/OROS/pharmacy/inpatient are source producers; Data Plane stores governed analytic copies only.
- Enterprise: MUSheX/COSTA/Coverage/GL/procurement/HR produce facts for analytics; Data Plane does not own enterprise SoR.
- Integration: integration-hub/connectors/FHIR adapters/IoT channels provide feed ingress, never SoR reassignment.
- Experience: dashboards/reporting/public-health UI surfaces consume BFF-orchestrated Data Plane outputs.

## Lifecycle Flows

1. Clinical-to-data: clinical events -> ingestion -> pipeline -> governance -> NDR/warehouse/reporting.
2. Registry-to-data: reference datasets -> governed joins -> analytics/public-health products.
3. Enterprise-to-data: enterprise facts -> governed ingest -> aggregate datasets.
4. Surveillance signal-to-action: signal -> case/worklist/escalation -> reporting.
5. Campaign planning-to-outcome: campaign definition -> dispatch/enrollment -> coverage/outcomes.
6. Report definition-to-export: report template -> run -> export/history -> audit.
7. Data access request-to-audit: request -> policy decision -> grant/deny -> audit/revocation.
8. AI registration-to-governance: model register/version -> approve/withdraw -> drift/inference monitoring.

## Active Architecture Decisions

- ADR-DATA-001: NDR consolidation (`ndr-service` + `national-data-repository-service`) remains in progress; runtime query ownership is now executed on `ndr-service`, structural merge/deprecation execution is pending.
