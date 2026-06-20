# Product Truth Implementation Waves

> Generated as part of Product Truth Recovery (2026-06-20)
> Source gap register: [product-truth-gap-register.md](../audits/product-truth-gap-register.md)

## Wave 0 — Complete

- Unified scanner: `scripts/completeness/generate-product-truth.mjs`
- Canonical dataset: `reports/product/product-truth.json`
- Audit artifacts (inventory, bidirectional traceability, gap register, blueprints, final report)
- Quality gate: `scripts/guard/check-product-truth.sh` (advisory, wired into local pipeline)
- Internal-only documentation: `docs/audits/internal-only/` (25 services)

## Wave 1 — Complete

**Result:** gaps **355 → 203** | Categories **F, G, M, N, O = 0**

See git history on `claude/product-truth-recovery` for F/G/N/O closure details.

## Wave 2 — Complete (BFF and contract completion)

**Result:** gaps **203 → 69** | Categories **C = 0** | **D priority 7 = 0** | Gate ratchet **69**

### Wave 2a — Complete

| Item | Outcome |
|------|---------|
| **Category C** | Closed — `nhume.openapi.yaml` + `vashandi-workforce.openapi.yaml` handler-synced contracts |
| **Contract matrix** | `vashandi.openapi.yaml` marked BFF-only; sovereign mapping for vashandi-workforce |
| **Scanner** | `route-registry.ts` merged into product-truth route scan; recursive hook/lib resolution for Vashandi/Nhume |
| **Tooling** | `scripts/completeness/emit-handler-synced-openapi.mjs` for handler→contract sync |

### Wave 2b — Complete

| Step | Outcome |
|------|---------|
| **1 — D partial wiring** | `SERVICE_UI_ALIASES` for GL, Msika flow/apps, Tshepo audit/identity/offline, butano-fhir — all seven now `frontendUi: real` |
| **2 — E thin pages** | `admin-governance-scaffold` backing signal for `/work/**` `ScopedAdministrationSurface` pages |
| **3 — Vashandi imports** | BFF `POST /internal/v1/vashandi/workforce-profiles/import-bridge`, OpenAPI schemas, `/work/vashandi/imports` wired via `useWorkforceImportBridge` |
| **4 — Gate ratchet** | `PRODUCT_TRUTH_VIOLATION_THRESHOLD` default **69** (was 99999) |

**Remaining (Wave 3+):** 69 gaps — **E: 43** (unwired feature pages), **D: 26** (other partial wiring).

## Wave 3 — Cross-service cohesion (in progress)

**Result:** **14/14 journeys pass** cohesion evaluation with golden-thread tests

| Item | Outcome |
|------|---------|
| **Journey registry** | `scripts/completeness/cross-service-journeys.mjs` — 14 canonical multi-service journeys |
| **Scanner** | `generate-product-truth.mjs` evaluates pass / needs-work / missing-test per journey |
| **Golden-thread tests** | 9 new cross-service vitest files under `ui/one-ui-shell/src/lib/__tests__/` |
| **Gate** | `scripts/guard/check-cross-service-cohesion.sh` (advisory; `COHESION_GATE_BLOCKING=1` to block) |

See [product-truth-cross-service-cohesion.md](../audits/product-truth-cross-service-cohesion.md).

**Remaining:** 69 product-truth gaps (E: 43, D: 26); runtime E2E validation of journeys on preview sandbox.

## Definition of done (per service)

Real user opens UI → performs workflow → saves/retrieves via BFF/API → persisted state reflected on refresh, with auth, policy, tenant context, tests, and error handling.

## Regenerate audit

```bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
node scripts/completeness/emit-handler-synced-openapi.mjs <maven-module> [path-prefix]
```
