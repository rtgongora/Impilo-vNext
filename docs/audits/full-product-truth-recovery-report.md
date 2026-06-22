# Full Product Truth Recovery Report

> Generated: 2026-06-20T13:50:43.992Z
> Branch: `claude/product-truth-recovery`

## Executive summary

| Metric | Count |
|--------|------:|
| Total services audited | 92 |
| Backend services | 92 |
| Shared libraries | 12 |
| Frontend surfaces (routes) | 614 |
| Mobile screens | 174 |
| BFF route handlers | 2145 |
| OpenAPI contracts | 102 |
| Services with DB persistence | 92 |
| **Phase 6 complete (user-facing + documented internal)** | **92** |
| User-facing services at `real` status | 67 / 67 |
| Services internal-only (documented) | 24 |
| Services partially complete | 0 |
| Services backend-only (no UI) | 0 |
| Services UI-only (no backend) | 0 |
| Services with mock/stub hits | 0 |
| Total classified gaps | 0 |
| Blocker gaps | 0 |
| High severity gaps | 0 |
| Cross-service cohesion | 14/14 pass |

## Quality gates added

- `scripts/guard/check-product-truth.sh` — product-truth gap gate (threshold 0)
- `scripts/guard/check-phase6-service-completion.sh` — Phase 6 completion gate
- `scripts/guard/check-cross-service-cohesion.sh` — cross-service journey cohesion
- Wired into `scripts/pipeline/run-local-quality-gates.sh`

## Artifacts produced

| Artifact | Path |
|----------|------|
| Canonical dataset | [product-truth.json](../../reports/product/product-truth.json) |
| Service inventory | [product-truth-service-inventory.md](./product-truth-service-inventory.md) |
| Backend→UI traceability | [product-truth-backend-ui-traceability.md](./product-truth-backend-ui-traceability.md) |
| Frontend→Backend traceability | [product-truth-frontend-backend-traceability.md](./product-truth-frontend-backend-traceability.md) |
| Gap register | [product-truth-gap-register.md](./product-truth-gap-register.md) |
| Completion blueprints | [service-completion-blueprints.md](../product/service-completion-blueprints.md) |

## Remaining gaps by severity

_None_

## Implementation status

**Phase 6 (full-stack service completion)** — user-facing services must reach `real` product status with BFF + web wiring (+ mobile where required). Internal-only services require documented rationale under `docs/audits/internal-only/`.

**Phase 7 (cross-service cohesion)** — 14/14 journeys pass with golden-thread tests and preview runtime smoke.

## Services requiring product-owner decision

See [product-truth-gap-register.md](./product-truth-gap-register.md#services-requiring-product-owner-decision).

## Regenerate

```bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
bash scripts/guard/check-phase6-service-completion.sh
```
