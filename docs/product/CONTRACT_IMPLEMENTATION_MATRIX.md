# Contract Implementation Matrix

> Generated: 2026-08-08T10:33:19.371Z
> OpenAPI operations: **4936** | AsyncAPI channels: **84**

## Summary

| Status | Count |
|--------|------:|
| implemented | 4904 |
| partial | 0 |
| missing | 93 |
| unowned-contract | 23 |
| contract-gap (handler exists — extend OpenAPI) | 1320 |
| contract-parse-errors | 0 |
| **violations (partial + missing + orphan + invalid contract)** | **1413** |

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

- `missing` aimodelregistry_post__ (ai-model-registry.openapi.yaml)
- `missing` aimodelregistry_get__ (ai-model-registry.openapi.yaml)
- `missing` booking_get__ (booking.openapi.yaml)
- `missing` booking_post__ (booking.openapi.yaml)
- `missing` campaigns_post__ (campaigns.openapi.yaml)
- `missing` campaigns_get__ (campaigns.openapi.yaml)
- `missing` cardprintagent_post__ (card-print.openapi.yaml)
- `missing` cardprintagent_get__ (card-print.openapi.yaml)
- `missing` channels_post__ (channels.openapi.yaml)
- `missing` channels_get__ (channels.openapi.yaml)
- `missing` costingengine_get__ (costa.openapi.yaml)
- `missing` costingengine_post__ (costa.openapi.yaml)
- `missing` coverageEligibilityCheckAlias (coverage.openapi.yaml)
- `missing` coverage_post__ (coverage.openapi.yaml)
- `missing` coverage_get__ (coverage.openapi.yaml)
- `missing` credentialverification_post__ (credential-verification.openapi.yaml)
- `missing` credentialverification_get__ (credential-verification.openapi.yaml)
- `missing` daidzai_get_root (daidzai.openapi.yaml)
- `missing` daidzai_post_root (daidzai.openapi.yaml)
- `missing` dataaccessgovernance_post__ (data-access-governance.openapi.yaml)
- `missing` dataaccessgovernance_get__ (data-access-governance.openapi.yaml)
- `missing` document_post__ (document-store.openapi.yaml)
- `missing` bffCoreTransactionPostCompatibility (experience-bff.openapi.yaml)
- `missing` bffClinicalTimelinePatient (experience-bff.openapi.yaml)
- `missing` experiencebff_get__clients_healthId_identity_summary (experience-bff.openapi.yaml)
- `missing` forms_post__ (forms.openapi.yaml)
- `missing` forms_get__ (forms.openapi.yaml)
- `missing` generalledger_get__ (general-ledger.openapi.yaml)
- `missing` generalledger_post__ (general-ledger.openapi.yaml)
- `missing` hrLeaveTypes (hr-payroll.openapi.yaml)
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
