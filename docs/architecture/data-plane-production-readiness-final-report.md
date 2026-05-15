# Data Plane Production Readiness Final Report

Date: 2026-05-15
Verdict: `PARTIAL WITH EXPLICIT BLOCKERS` (not READY)

## What Was Completed

- Removed Data-plane BFF synthetic success on high-risk routes:
  - `AiGovernanceController` (proxy fallback masking removed; unavailable routes explicit `501`).
  - `AdminReportJobController` and `ReportJobController` fail-close behavior (including null/invalid upstream payload guards).
  - `MobileGovernanceController` fail-close behavior on governance/notification failures.
- Added bounded Data-plane BFF wiring for AI model registry:
  - `/internal/v1/ai/models*`, `/inference-records`, `/drift-events`.
  - Compatibility alias documented for `/internal/v1/ai-governance/*` model-registry paths.
- Wired public-health weekly/write surfaces to sovereign surveillance primitives with typed fail-close:
  - `/internal/v1/public-health/weekly-idsr` -> sovereign `/internal/v1/public-health/weekly-idsr` surveillance lifecycle API.
  - `/internal/v1/public-health/outbreaks` -> sovereign `/internal/v1/public-health/outbreaks` surveillance lifecycle API.
  - `/internal/v1/public-health/field-operations` -> sovereign `/internal/v1/public-health/field-operations` surveillance lifecycle API.
- Hardened `data-warehouse-service` query path for tenant isolation by replacing unscoped `findAll` with tenant-scoped repository queries.
- Expanded OpenAPI for Data-plane service surfaces and new/changed BFF routes.
- Wired `/reports` national dashboard DHIS2 tab to governed warehouse `national-kpis` aggregates (gold dataset stats) with explicit unavailable states for still-unwired tabs.
- Resolved NDR runtime owner overlap by making `ndr-service` the canonical runtime query owner and converting `national-data-repository-service` `/internal/v1/query` into explicit conflict/deprecation signaling.
- Produced Data-plane architecture and flow documentation set.

## Evidence-Backed Blockers

1. Data-plane contract/test depth remains partial across long-tail services outside the bounded remediation surface.
2. National dashboard tabs beyond warehouse-backed DHIS2 aggregates (facility/disease/mortality/immunization) still require governed dataset delivery.

## Ready/Not-Ready Decision

- Data Plane is **not** marked `READY`.
- Current state is production-hardened in targeted high-value gaps with runtime NDR ownership clarified and dedicated public-health lifecycle APIs in place; remaining readiness work is long-tail contract/test and dataset breadth convergence.
