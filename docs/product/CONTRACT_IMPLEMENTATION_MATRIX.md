# Contract Implementation Matrix

> Generated: 2026-08-08T11:38:05.241Z
> OpenAPI operations: **4936** | AsyncAPI channels: **84**

## Summary

| Status | Count |
|--------|------:|
| implemented | 4903 |
| partial | 0 |
| missing | 94 |
| unowned-contract | 23 |
| contract-gap (handler exists — extend OpenAPI) | 1305 |
| contract-parse-errors | 0 |
| **violations (partial + missing + orphan + invalid contract)** | **1399** |

## Remediation doctrine: complete — never delete

When the matrix reports a gap:

| Status | Action |
|--------|--------|
| **missing** | Implement the handler, service logic, persistence, and tests — wire BFF + UI |
| **contract-gap** | Add the operation to OpenAPI (sync-handler-routes-to-contract.mjs) — do not remove the controller |
| **partial** | Replace stub/501/TODO with real domain logic |
| **unowned-contract** | Assign contract to owning module in registry + openapi-contracts.mjs |

Forbidden: deleting controllers, removing routes, or trimming contracts to make the gate pass.


## Sample violations (first 40)

- `missing` coverageEligibilityCheck (coverage.openapi.yaml)
- `missing` coverageEligibilityCheckAlias (coverage.openapi.yaml)
- `missing` daidzai_delete_internal_v1 (daidzai.openapi.yaml)
- `missing` daidzai_get_internal_v1 (daidzai.openapi.yaml)
- `missing` daidzai_patch_internal_v1 (daidzai.openapi.yaml)
- `missing` daidzai_post_internal_v1 (daidzai.openapi.yaml)
- `missing` daidzai_put_internal_v1 (daidzai.openapi.yaml)
- `missing` daidzai_delete_internal_v1_daidzai (daidzai.openapi.yaml)
- `missing` daidzai_get_internal_v1_daidzai (daidzai.openapi.yaml)
- `missing` daidzai_patch_internal_v1_daidzai (daidzai.openapi.yaml)
- `missing` daidzai_post_internal_v1_daidzai (daidzai.openapi.yaml)
- `missing` daidzai_put_internal_v1_daidzai (daidzai.openapi.yaml)
- `missing` daidzai_delete_internal_v1_daidzai_disasters (daidzai.openapi.yaml)
- `missing` daidzai_patch_internal_v1_daidzai_disasters (daidzai.openapi.yaml)
- `missing` daidzai_put_internal_v1_daidzai_disasters (daidzai.openapi.yaml)
- `missing` bffCoreTransactionPostCompatibility (experience-bff.openapi.yaml)
- `missing` internalHealth (experience-bff.openapi.yaml)
- `missing` bffClinicalTimelinePatient (experience-bff.openapi.yaml)
- `missing` experiencebff_post__application_dicom (experience-bff.openapi.yaml)
- `missing` experiencebff_get__clients_healthId_identity_summary (experience-bff.openapi.yaml)
- `missing` hrLeaveTypes (hr-payroll.openapi.yaml)
- `missing` hrpayroll_post__internal_v1_hr_leave_types (hr-payroll.openapi.yaml)
- `missing` hrLeaveRequests (hr-payroll.openapi.yaml)
- `missing` hrpayroll_post__internal_v1_hr_leave_requests (hr-payroll.openapi.yaml)
- `missing` hrAttendance (hr-payroll.openapi.yaml)
- `missing` hrpayroll_post__internal_v1_hr_attendance (hr-payroll.openapi.yaml)
- `missing` hrpayroll_get__internal_v1_hr_leave_balances (hr-payroll.openapi.yaml)
- `missing` hrpayroll_post__internal_v1_hr_leave_balances (hr-payroll.openapi.yaml)
- `missing` indawo_get__ (indawo.openapi.yaml)
- `missing` indawo_post__ (indawo.openapi.yaml)
- `contract-gap` GET /internal/v1/equipment (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment (asset-registry-service)
- `contract-gap` GET /internal/v1/equipment/{equipment_id} (asset-registry-service)
- `contract-gap` GET /internal/v1/equipment/{equipment_id}/detail (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/{equipment_id}/metadata (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/{equipment_id}/status (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/{equipment_id}/transfers (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/transfers/{transfer_id}/approve (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/transfers/{transfer_id}/receive (asset-registry-service)
- `contract-gap` POST /internal/v1/equipment/{equipment_id}/maintenance (asset-registry-service)

_Regenerate: `npm run contract-matrix --prefix scripts/completeness`_
