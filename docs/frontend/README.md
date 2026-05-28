# Frontend Parity Documentation (Authoritative)

This directory is the **canonical** location for vNext frontend surfacing, parity, and doctrine-alignment artifacts.

Root-level copies (`ROUTE_MAP.md`, `FRONTEND_ARCHITECTURE.md`, `WEB_MOBILE_PARITY_MATRIX.md`) remain as compatibility pointers; update here first, then sync summaries at repo root.

## Documents

| Document | Purpose |
|----------|---------|
| [BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md](./BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md) | Backend capability → UI route/client/maturity inventory |
| [ROUTE_SURFACING_CLEANUP.md](./ROUTE_SURFACING_CLEANUP.md) | Route/launcher discoverability and dead-end remediation |
| [WEB_MOBILE_SURFACING_PARITY.md](./WEB_MOBILE_SURFACING_PARITY.md) | Cross-surface parity truth |
| [FRONTEND_IMPLEMENTATION_STATUS.md](./FRONTEND_IMPLEMENTATION_STATUS.md) | Live/partial/fixture/not-wired/blocked status |
| [DOCTRINE_ALIGNMENT_CHECKLIST.md](./DOCTRINE_ALIGNMENT_CHECKLIST.md) | Per-surface doctrine alignment |
| [REMAINING_FRONTEND_GAPS.md](./REMAINING_FRONTEND_GAPS.md) | Open gaps with priority |
| [NEXT_FRONTEND_WAVE_RECOMMENDATIONS.md](./NEXT_FRONTEND_WAVE_RECOMMENDATIONS.md) | Follow-on wave recommendations |
| [MATURITY_TAXONOMY.md](./MATURITY_TAXONOMY.md) | Canonical maturity labels |

## Canonical surfaces

| Surface | Path | Route count (2026-05-28) |
|---------|------|--------------------------|
| Web shell (canonical) | `ui/one-ui-shell` | 374 (`EXPECTED_ROUTE_COUNT`) |
| Citizen mobile | `apps/mobile/citizen-app` | Tab + section router |
| Provider mobile | `apps/mobile/provider-app` | Mode + clinical tools router |
| Satellite UIs | `ui/*-web`, `ui/*-console`, `ui/portal` | Per-app |

> The previous `ui/experience` web continuity fork (258 routes) was merged
> into `ui/one-ui-shell` and removed via the GAP-010 convergence on
> 2026-05-28. See [`CONVERGENCE_INVENTORY.md`](./CONVERGENCE_INVENTORY.md)
> for the full merge contract and per-file disposition.

## Maturity labels (required on every major surface)

- **Live** — Real BFF/API data; production-safe workflow.
- **Partial** — Some live paths; depth or write flows incomplete.
- **Fixture** — Demo/sample data; must be labelled and isolated.
- **Not wired** — Route/shell exists; no backend connection.
- **Blocked** — Intentionally unavailable (policy, missing contract, RTC, etc.).

## Related audits (source material)

- `docs/audits/BACKEND_NOT_SURFACED_REGISTER.md`
- `docs/audits/BACKEND_CAPABILITY_SURFACE_MAP.md`
- `docs/audits/FRONTEND_BACKEND_WIRING_MATRIX.md`
- `docs/registry/backend-to-frontend-wiring-map.md`

## Regeneration

```bash
node scripts/frontend/generate-parity-docs.mjs
```
