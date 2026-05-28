# Frontend Implementation Status

> Updated: 2026-05-28

## Route registries

| Surface | Registry | Count |
|---------|----------|-------|
| Web canonical | `ui/one-ui-shell/src/lib/routes.ts` | 374 |
| Citizen mobile | Tab + Personal section router | 7 tabs, 30+ sections |
| Provider mobile | Mode router + ClinicalTools | 5 modes, 30+ tools |

> The previous `ui/experience` continuity registry (258 routes) was merged
> into `ui/one-ui-shell` and removed via the GAP-010 convergence on
> 2026-05-28. See [`CONVERGENCE_INVENTORY.md`](./CONVERGENCE_INVENTORY.md).

## Live functionality (representative)

- Queue/triage/worklist (web + mobile)
- EHR summary/timeline (web BFF)
- Social timeline/communities (web + mobile)
- Core transaction feed (web BFF — no fixture injection)
- Marketplace launcher (web + mobile BFF)
- Public health reads + fail-close writes (web)
- Teleconsult lifecycle (web; RTC blocked)

## Partial functionality

- Trust admin, registry identity ops, finance/commerce, learning, Nompilo, workflow/dispatch ops, Ndila intelligence, UBOMI

## Fixture / demo (must be labelled)

- Legacy journey doctrine scaffolding where explicitly badged
- Nompilo mobile fallback copy when LLM unavailable

## Not wired

- UBOMI civil registry workflows (page is honest placeholder)
- Some marketplace list routes (501 from BFF)
- Admin keys, federation registry (blocked pending contract)

## Blocked

- Teleconsult RTC/media (intentional)
- Raw MusheX `/mushex/v1` browser pass-through

## Mobile parity status

See [WEB_MOBILE_SURFACING_PARITY.md](./WEB_MOBILE_SURFACING_PARITY.md).

## Nompilo grounding

- Global command bar + `/ask` route
- Journey-aware suggestions via `classifyRouteJourney`
- Context query: `?from=` pathname on Ask navigation

## Build / test (2026-05-28 sweep)

| Command | Surface | Result |
|---------|---------|--------|
| `npm run type-check` | `ui/one-ui-shell` | **Pass** |
| `npm run test` | `ui/one-ui-shell` | **Pass** (530 tests) |
| `npm run lint` | `ui/one-ui-shell` | **Pass** (warnings only, pre-existing) |
| `npm run build` | `ui/one-ui-shell` | Not run (long); type-check + tests green |

```bash
cd ui/one-ui-shell && npm run type-check && npm run test && npm run lint
cd apps/mobile/citizen-app && npm run test   # recommended follow-up
```
