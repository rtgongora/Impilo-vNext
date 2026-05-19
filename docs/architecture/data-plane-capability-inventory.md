# Data Plane Capability Inventory

Date: 2026-05-15
Verdict: `PARTIAL WITH EXPLICIT BLOCKERS`

## Ownership and Merge Baseline

- Canonical Data Plane owners in this pass: `data-ingestion-service`, `data-pipeline-service`, `data-governance-service`, `data-access-governance-service`, `ndr-service`, `national-data-repository-service`, `reporting-service`, `data-warehouse-service`, `surveillance-service`, `campaigns-service`, `ai-model-registry-service`, `experience-bff` (orchestration only).
- `ndr-service` and `national-data-repository-service` are treated as a merge target:
  - `ndr-service` currently owns ingest + governed query API under `/internal/v1/ndr/*`.
  - `national-data-repository-service` owns dataset catalog under `/internal/v1/datasets*` and explicitly rejects runtime query execution on `/internal/v1/query` with owner metadata (`ndr-service`).
  - Runtime ownership overlap is resolved for active query traffic while structural service merge remains a follow-up architecture task.

## Capability Matrix

| Capability | Owner service | Current API | Current UI/BFF surface | Implementation status | Production blocker | Recommended next step |
|---|---|---|---|---|---|---|
| Inbound ingestion receipts and validation | `data-ingestion-service` | internal ingest routes (service-local) | no direct UI | partial | endpoint inventory/contract depth | add contract IT + endpoint registry entries |
| Pipeline orchestration and run status | `data-pipeline-service` | internal pipeline routes | no direct UI | partial | state machine not fully documented in service | align run statuses to canonical state model |
| Governance dataset/rules/policy APIs | `data-governance-service` | `/internal/v1/governance/*`, `/external/v1/exports` | `experience-bff` via `/internal/v1/ai-governance/*` and `/internal/v1/mobile/provider/governance/*` | implemented-or-partial | historical BFF status masking | remediated in this pass for fail-close; add deeper coverage |
| Governed data access (DAGS) | `data-access-governance-service` | internal access governance routes | BFF data-access controllers (existing) | partial | purpose-of-use/audit consistency gaps | enforce canonical decision/audit envelope |
| NDR ingest + bronze/gold governed query | `ndr-service` | `/internal/v1/ndr/ingest/events`, `/query/bronze`, `/query/gold/encounters`, `/build/gold/encounters` | indirect via reporting/data workflows | implemented-or-partial | broader NDR merge still pending | maintain canonical runtime owner; execute eventual service consolidation |
| Dataset catalog/query for national repository | `national-data-repository-service` | `/internal/v1/datasets*`, `/internal/v1/query` (conflict/deprecation signaling) | no direct UI | partial | long-term merge/program governance | keep catalog scope; route runtime queries to `ndr-service` only |
| Report execution and run history | `reporting-service` | `/internal/v1/reports*`, `/internal/v1/reports/tenant-runs` | BFF `/internal/v1/reports/*`, `/internal/v1/admin/reports/jobs` | implemented-or-partial | previous BFF synthetic/empty success and unstable job identifiers | remediated in this pass with typed fail-close and strict run-id validation |
| Gold dataset query/materialization | `data-warehouse-service` | `/internal/v1/gold/*`, `/external/v1/gold/datasets` | no direct UI | implemented-or-partial | cross-tenant read risk in query | remediated in this pass with tenant-scoped repository reads |
| Surveillance signals/cases/alerts/counters/public-health lifecycle | `surveillance-service` | `/internal/v1/signals`, `/internal/v1/cases`, `/internal/v1/surveillance/*`, `/internal/v1/ingest`, `/internal/v1/public-health/*` | BFF `/internal/v1/public-health/*` | implemented-or-partial | long-tail contract/test depth | maintain sovereign lifecycle APIs and expand endpoint tests |
| Campaign lifecycle and dispatch | `campaigns-service` | `/internal/v1/campaigns*`, `/{id}/close`, `/{id}/dispatch`, `/{id}/enroll` | BFF `/internal/v1/public-health/campaigns*` | implemented-or-partial | OpenAPI drift and incomplete UI parity | contract expanded; keep unavailable writes explicit where missing |
| AI model registry and governance metadata | `ai-model-registry-service` | `/internal/v1/ai-registry/*` | BFF `/internal/v1/ai/*` (added this pass; `/internal/v1/ai-governance/*` compatibility aliases) | partial | security hardening and broader controller coverage still partial | keep new BFF wiring and alias contract; expand service/controller test depth |
| Public health orchestration | `experience-bff` | `/internal/v1/public-health/*` | `ui/experience` public-health tabs | implemented-or-partial | long-tail public-health UX/test depth | keep dedicated lifecycle wiring fail-close and expand tab-level dataset integration |

## Evidence Notes

- BFF fake-success removed for:
  - `AiGovernanceController` read/write fallback masking.
  - `AdminReportJobController` list fallback masking.
  - `ReportJobController` synthetic empty detail response.
  - `MobileGovernanceController` forced `status: LIVE` with hidden upstream errors.
- Added bounded Data-plane BFF wiring for AI model registry (`/internal/v1/ai/*`).
- Added tenant-isolated gold query enforcement in `data-warehouse-service`.
