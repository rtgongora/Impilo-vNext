# Contract Usage Audit

## Target Contracts

- `contracts/core-transaction.ts`
- `contracts/openapi/*.yaml`
- `contracts/asyncapi/*.yaml`
- `contracts/health-os-identifiers.ts`

## Findings

| ID | Severity | Contract | Finding | Impact | Remediation |
|---|---|---|---|---|---|
| CON-001 | HIGH | `contracts/core-transaction.ts` | Doctrine UI pages use local duplicate types/fixtures instead of canonical contract imports. | Drift between canonical state model and rendered UI | Replace local types in `features/core-transaction/types.ts` and wire API-driven models. |
| CON-002 | MEDIUM | `contracts/health-os-identifiers.ts` vs shared-ui contracts | Enum divergence (`ActorType` mismatch) observed. | Cross-surface trust/context drift | Unify shared source or generate shared package from canonical contract. |
| CON-003 | MEDIUM | OpenAPI contracts | Frontend clients are mostly handwritten; no strict codegen lockstep. | Silent drift possible | Introduce generated client checks or schema conformance tests per critical surface. |
| CON-004 | MEDIUM | AsyncAPI core transaction events | Schema reference mostly documented, limited runtime validation binding. | Event payload drift risk | Add event schema contract tests in publishers/consumers. |

## Easy Drift Fixes Completed

- Added explicit fixture honesty labels on doctrine pages to reduce false-live interpretation while contract adoption remains pending.
