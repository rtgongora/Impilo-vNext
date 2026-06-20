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

### Delivered

| Category | Outcome |
|----------|---------|
| **F** | Cleared — scanner heuristics; media metadata display fix |
| **G** | Cleared — form persistence false positives; `/operations/assets` wired to BFF |
| **N** | Cleared (64 → 0) — authz readiness scanner, `SecurityBaselineConfig` rollout, `TrustContextFilter` fixes, referral/rtc/scheduling Postgres + outbox |
| **M** | Cleared — oros, pct, simba already have mobile surfaces (scanner confirms) |
| **O** | Cleared (5 → 0) — contract-surface tests for general-ledger, guidance, hr-payroll, mushe-wallet, procurement |
| **E triage** | Reduced **91 → 67** — navigation-hub / route-delegation detection for shell pages, redirects, and card-grid hubs |

### Replace, not remove

Wave 1 treats “remove mock/stub” as **replace with real service-backed features**, never delete routes.

### Remaining after Wave 1 (Wave 2+ scope)

| Cat | Count | Notes |
|-----|------:|-------|
| **D** | 134 | Partial frontend/BFF wiring — primary Wave 2 target |
| **E** | 67 | Thin feature pages without detected hooks (not shell hubs); wire or implement in Wave 2 |
| **C** | 2 | nhume-service, vashandi-workforce-service contract gaps |

## Wave 2 — BFF and contract completion (next)

- Close remaining category **C** contract gaps
- Category **D** partial wiring (hooks exist but pages thin)
- Remaining **E** thin pages — wire to BFF or implement domain flows
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
