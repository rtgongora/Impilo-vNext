# Route Surfacing Cleanup

> Updated: 2026-05-28

## Canonical route registries

| Surface | Registry | Count |
|---------|----------|-------|
| Web (canonical) | `ui/one-ui-shell/src/lib/routes.ts` | **374** |
| Journey groups | `ui/one-ui-shell/src/lib/ui-route-journey-map.ts` | — |

> The `ui/experience` continuity fork (258 routes) was merged into
> `ui/one-ui-shell` and removed via the GAP-010 convergence on
> 2026-05-28. The four additional routes captured in the canonical
> count (`/ehr/[patientId]/preferences/communications` and three
> `/operations/facility-operations/*` sub-routes) were lifted as
> part of Phase 1f.

## Launcher cleanup

| Entry type | Source | Rules applied |
|------------|--------|---------------|
| Static shell apps | `ui/one-ui-shell/src/lib/shell/app-registry.ts` | Role gate + href; maturity metadata on `AppDefinition` |
| Marketplace apps | `useHealthOsLauncher` → `/internal/v1/launcher/apps` | Disabled state + reason when not launchable |
| Mobile Health OS | `healthOsLauncherService.ts` (citizen/provider) | Same BFF; partial maturity on screen |

## Intentionally hidden / API-only / blocked

| Capability | Route / entry | Status | Reason |
|------------|---------------|--------|--------|
| UBOMI CRVS workflows | `/ubomi` | Not wired | No canonical BFF bridge |
| Admin API keys | `/admin/keys` | Blocked | Typed key-management BFF unavailable |
| Federation registry | `/admin/federation` | Blocked | Awaiting canonical contract |
| Teleconsult RTC/media | `/telemedicine/session/*` | Blocked | Real-time transport not production-ready |
| Raw MusheX browser API | — | API-only | Finance via `/internal/v1/finance/*` only |
| ZIBO terminology admin | `ui/zibo-web` | Satellite app | Direct `/v1/*` to ZIBO service |

## Dead-end remediation (this sweep)

| Issue | Fix |
|-------|-----|
| Route count doc drift (346 vs 370) | Root `ROUTE_MAP.md` + `FRONTEND_ARCHITECTURE.md` updated |
| Citizen telehealth hidden in tab bar | Added **Care** tab (`telehealth`) |
| Provider hub web_path only | `ProfessionalHubBody` opens `EXPO_PUBLIC_WEB_SHELL_URL` + path |
| Marketplace tiles without reason | `ShellStartMenu` shows state label for non-installed apps |
| UBOMI placeholder without maturity | `/ubomi` page with `FeatureMaturityBadge` + honest not-wired copy |

## Satellite UI apps (discoverable via docs / deploy)

`butano-web`, `costa-console`, `msika-web`, `zibo-web`, `pct-web`, `oros-web`, `pharmacy-web`, `mushex-*`, `msika-flow-*`, `portal`, `ops-console`, `developer-console`, `support-console`, `inventory-web`, `knowledge-admin`, `self-service`

Each maintains its own API module; parity matrix marks web-only where applicable.
