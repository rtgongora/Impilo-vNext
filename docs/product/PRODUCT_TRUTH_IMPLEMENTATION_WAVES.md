# Product Truth Implementation Waves

> Generated as part of Product Truth Recovery (2026-06-20)
> Source gap register: [product-truth-gap-register.md](../audits/product-truth-gap-register.md)

## Wave 0 — Complete (this pass)

- Unified scanner: `scripts/completeness/generate-product-truth.mjs`
- Canonical dataset: `reports/product/product-truth.json`
- Audit artifacts (inventory, bidirectional traceability, gap register, blueprints, final report)
- Quality gate: `scripts/guard/check-product-truth.sh` (advisory, wired into local pipeline)
- Internal-only documentation: `docs/audits/internal-only/` (25 services)

## Wave 1 — High-impact user-facing gaps (in progress)

Completed in this branch:

- **Category G cleared** — form persistence false positives removed; `/operations/assets` wired to asset-registry BFF (`useAssets`, `useUpsertAsset`, status/retire mutations)
- **Category F reduced** — scanner heuristics refined (inline BFF fetch, domain clients, JSON debug vs API payload); media asset detail uses structured metadata display
- **Replace, not remove** — Wave 1 treats “remove mock/stub” as **replace with real service-backed features**, never delete routes

Remaining Wave 1 scope:

1. **Category N — largely closed** (64 → 3): code-based authz/audit readiness scanner; Wave 14 `SecurityBaselineConfig` emitted for 73 services; `TrustContextFilter` added to community, coverage, clinical-knowledge, ndila, and five services that lacked `SecurityConfig`. **Remaining N (3):** stateless skeleton services without DB/outbox — `referral-service`, `rtc-gateway-service`, `scheduling-service` (need Flyway + tenant-scoped persistence, not registry-only status bumps).
2. **Category M** — Mobile parity for oros, pct, simba
3. **Category O** — Primary-workflow tests for zero-coverage services
4. **Category E** (91) — Shell/navigation routes flagged without BFF backing (many false positives; triage separately)

Priority order from gap register (service-level):

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
