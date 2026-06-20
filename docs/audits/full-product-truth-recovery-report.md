# Full Product Truth Recovery Report

> Generated: 2026-06-20T11:50:37.471Z
> Branch: `claude/product-truth-recovery`

## Executive summary

| Metric | Count |
|--------|------:|
| Total services audited | 92 |
| Backend services | 92 |
| Shared libraries | 12 |
| Frontend surfaces (routes) | 615 |
| Mobile screens | 174 |
| BFF route handlers | 2145 |
| OpenAPI contracts | 102 |
| Services with DB persistence | 92 |
| Services fully/mostly complete | 67 |
| Services partially complete | 0 |
| Services backend-only (no UI) | 0 |
| Services UI-only (no backend) | 0 |
| Services with mock/stub hits | 0 |
| Total classified gaps | 69 |
| Blocker gaps | 0 |
| High severity gaps | 43 |

## Quality gates added

- `scripts/guard/check-product-truth.sh` — advisory gate driven by `reports/product/product-truth.json`
- Wired into `scripts/pipeline/run-local-quality-gates.sh` (advisory phase)

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

- **high:** 43
- **low:** 26

## Implementation status

Phase 6 (service-by-service fixes) and Phase 7 (cross-service cohesion) are documented in the gap register priority list. Wave 1 should target blocker/high gaps in user-facing clinical, registry, and finance domains.

## Services requiring product-owner decision

See [product-truth-gap-register.md](./product-truth-gap-register.md#services-requiring-product-owner-decision).

## Regenerate

```bash
cd scripts/completeness && npm run product-truth
bash scripts/guard/check-product-truth.sh
```
