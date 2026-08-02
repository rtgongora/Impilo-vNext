# Checkpoint 2 — Canonical trust contracts

**Branch:** `claude/tshepo-trust-cp1-truth-audit`  
**Depends on:** Checkpoint 1 closure (`docs(security): close checkpoint-1 trust evidence gaps`)  
**Status after conformance closure:** see final report in the closing commit message / agent report.

## Artefacts

| Artefact | Path |
|---|---|
| Java v1 types + adapters | `libs/tshepo-contracts/src/main/java/.../v1/` |
| Java unit / adapter / audit / fixture tests | `libs/tshepo-contracts/src/test/java/.../v1/` |
| TypeScript mirror + runtime validators | `contracts/trust-decision/{v1,validate,index}.ts` |
| Shared fixtures | `contracts/trust-decision/fixtures/trust-decision-v1.fixtures.json` |
| Versioning / deprecation rules | `contracts/trust-decision/VERSIONING.md` |
| JSON Schema | `contracts/schemas/trust-decision-v1.schema.json` |
| OpenAPI components | `contracts/openapi/tshepo-trust-decision-v1.openapi.yaml` |
| Parity checker | `scripts/completeness/check-trust-contract-parity.mjs` |
| OpenAPI lint + bundle | `scripts/completeness/lint-trust-decision-openapi.mjs` |
| Fixture schema validator | `scripts/guard/validate-trust-decision-fixtures.py` |
| Contract CI gate | `scripts/guard/check-trust-decision-contracts.sh` (wired into `scripts/test/run-api-contract-checks.sh`) |
| Adapter registry | [COMPATIBILITY_ADAPTERS.md](COMPATIBILITY_ADAPTERS.md) |
| Browser BFF session evidence | [BROWSER_BFF_SESSION_EVIDENCE.md](BROWSER_BFF_SESSION_EVIDENCE.md) |

## Conformance layers (must agree)

1. Field/enum parity across Java, TypeScript, JSON Schema, OpenAPI
2. Shared fixtures validated by Java records, TypeScript validators, Draft 2020-12 JSON Schema (with RFC3339 format checker), and OpenAPI-compatible models
3. Adapters fail closed / never broaden AAL, authority, scope, context or consent
4. Consumers import only the canonical package entry points (duplicate-declaration guard)

## Non-goals (this checkpoint)

- No Envoy/OPA/OAuth/work-context enforcement activation
- No bypass removal
- No endpoint/header retirement
- No Mvumo/consent ownership resolution
- No deploy / merge / credential rotation
- Checkpoint 3 not started
