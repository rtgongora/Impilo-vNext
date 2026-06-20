# Contract Implementation Matrix

> Generated: 2026-06-20T11:43:44.918Z
> OpenAPI operations: **4591** | AsyncAPI channels: **84**

## Summary

| Status | Count |
|--------|------:|
| implemented | 4652 |
| partial | 0 |
| missing | 0 |
| unowned-contract | 23 |
| contract-gap (handler exists — extend OpenAPI) | 3 |
| contract-parse-errors | 0 |
| **violations (partial + missing + orphan + invalid contract)** | **3** |

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

- `contract-gap` GET /internal/v1/governance/decision-audit (data-governance-service)
- `contract-gap` GET /api/v1/ndila/tiles/{z}/{x}/{y}.png (ndila-service)
- `contract-gap` GET /api/v1/maps/tiles/{z}/{x}/{y}.png (ndila-service)

_Regenerate: `npm run contract-matrix --prefix scripts/completeness`_
