# Product Truth Implementation Waves

> Generated as part of Product Truth Recovery (2026-06-20)
> Source gap register: [product-truth-gap-register.md](../audits/product-truth-gap-register.md)

## Wave 0 — Complete (this pass)

- Unified scanner: `scripts/completeness/generate-product-truth.mjs`
- Canonical dataset: `reports/product/product-truth.json`
- Audit artifacts (inventory, bidirectional traceability, gap register, blueprints, final report)
- Quality gate: `scripts/guard/check-product-truth.sh` (advisory, wired into local pipeline)
- Internal-only documentation: `docs/audits/internal-only/` (25 services)

## Wave 1 — High-impact user-facing gaps (next)

Priority order from gap register (service-level):

1. **Category F** — Remove mock/stub/fixture from production UI surfaces flagged in frontend traceability
2. **Category G** — Wire form submissions to persist (mutations + invalidateQueries)
3. **Category N** — Auth/policy/tenant scoping on services with `authz_audit_status: partial`
4. **Category M** — Mobile parity for oros, pct, simba (extend provider/citizen BFF mobile routes)
5. **Category O** — Add primary-workflow tests for services with zero test coverage

## Wave 2 — BFF and contract completion

- Close remaining category **C** contract gaps
- Category **D** partial wiring (hooks exist but pages thin)
- Ratchet `PRODUCT_TRUTH_VIOLATION_THRESHOLD` from 99999 toward measured baseline

## Wave 3 — Cross-service cohesion

Validate journeys in [product-truth-cross-service-cohesion.md](../audits/product-truth-cross-service-cohesion.md):

- identity → registry → SHR
- orders → labs/imaging/inventory
- telemedicine → PCT
- learning → provider registry
- costing → payments

## Definition of done (per service)

Real user opens UI → performs workflow → saves/retrieves via BFF/API → persisted state reflected on refresh, with auth, policy, tenant context, tests, and error handling.

## Regenerate audit

```bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
```
