# Contract Implementation Matrix

> Generated: 2026-06-07T08:11:35.618Z
> OpenAPI operations: **4451** | AsyncAPI channels: **84**

## Summary

| Status | Count |
|--------|------:|
| implemented | 4535 |
| partial | 0 |
| missing | 0 |
| unowned-contract | 0 |
| contract-gap (handler exists — extend OpenAPI) | 0 |
| contract-parse-errors | 0 |
| **violations (partial + missing + orphan + invalid contract)** | **0** |

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

_No violations detected._

_Regenerate: `npm run contract-matrix --prefix scripts/completeness`_
