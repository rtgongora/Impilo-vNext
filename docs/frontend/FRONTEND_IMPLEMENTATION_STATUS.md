# Frontend Implementation Status

> Updated: 2026-06-11

## Route registries

| Surface | Registry | Count |
|---------|----------|-------|
| Web canonical | `ui/one-ui-shell/src/lib/routes.ts` | 370 |
| Web continuity | `ui/one-ui-shell/src/lib/routes.ts` | 258 |
| Citizen mobile | Tab + Personal section router | 7 tabs, 30+ sections |
| Provider mobile | Mode router + ClinicalTools | 5 modes, 30+ tools |

## Live functionality (representative)

- Queue/triage/worklist (web + mobile)
- EHR summary/timeline (web BFF)
- Social timeline/communities (web + mobile)
- Core transaction feed (web BFF â€” no fixture injection)
- Marketplace launcher (web + mobile BFF)
- Public health ops: site registry geo, surveillance investigations, campaigns enroll, Ndila PH maps (web)
- Data pipelines: watermarks, warehouse gold, NDR catalog via BFF (web)
- Teleconsult lifecycle (web; RTC blocked)
- Telemedicine analytics ingest persisted (analytics-pipeline-service)

## Partial functionality

- Trust admin, registry identity ops, finance/commerce, learning, Nompilo, workflow/dispatch ops, UBOMI
- Public health mobile (provider field ops + site registry reads; citizen summary/alerts)

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

## Build / test (run per workspace)

```bash
cd ui && npm run type-check
cd ui/one-ui-shell && npm run test && npm run build
cd apps/mobile && pnpm test
```
