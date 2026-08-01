# Checkpoint 2 — Canonical trust contracts

**Branch:** `claude/tshepo-trust-cp1-truth-audit`  
**Depends on:** Checkpoint 1 closure (`docs(security): close checkpoint-1 trust evidence gaps`)

## Artefacts

| Artefact | Path |
|---|---|
| Java v1 types + adapters | `libs/tshepo-contracts/src/main/java/.../v1/` |
| Java tests | `libs/tshepo-contracts/src/test/java/.../v1/TrustContractsV1Test.java` |
| TypeScript mirror | `contracts/trust-decision/v1.ts` |
| JSON Schema | `contracts/schemas/trust-decision-v1.schema.json` |
| OpenAPI components | `contracts/openapi/tshepo-trust-decision-v1.openapi.yaml` |
| Adapter registry | [COMPATIBILITY_ADAPTERS.md](COMPATIBILITY_ADAPTERS.md) |

## Non-goals (this checkpoint)

- No Envoy/OPA/OAuth/work-context enforcement activation
- No bypass removal
- No endpoint/header retirement
- No Mvumo/consent ownership resolution
- No deploy / merge / credential rotation
