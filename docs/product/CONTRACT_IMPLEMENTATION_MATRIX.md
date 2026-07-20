# Contract Implementation Matrix

> Generated: 2026-07-20T18:15:10.507Z
> OpenAPI operations: **4829** | AsyncAPI channels: **84**

## Summary

| Status | Count |
|--------|------:|
| implemented | 4879 |
| partial | 0 |
| missing | 11 |
| unowned-contract | 23 |
| contract-gap (handler exists — extend OpenAPI) | 55 |
| contract-parse-errors | 0 |
| **violations (partial + missing + orphan + invalid contract)** | **66** |

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
- `contract-gap` GET /internal/v1/governance/decision-audit (data-governance-service)
- `contract-gap` POST /internal/v1/governance/data-subject-requests (data-governance-service)
- `contract-gap` GET /internal/v1/governance/data-subject-requests (data-governance-service)
- `contract-gap` POST /internal/v1/governance/data-subject-requests/cancel (data-governance-service)
- `contract-gap` GET /internal/v1/governance/privacy-preferences (data-governance-service)
- `contract-gap` PUT /internal/v1/governance/privacy-preferences (data-governance-service)
- `contract-gap` GET /internal/v1/governance/display-settings (data-governance-service)
- `contract-gap` PUT /internal/v1/governance/display-settings (data-governance-service)
- `contract-gap` POST /internal/v1/deid/datasets (data-governance-service)
- `contract-gap` GET /internal/v1/deid/datasets (data-governance-service)

_Regenerate: `npm run contract-matrix --prefix scripts/completeness`_
