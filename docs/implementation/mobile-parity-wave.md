# Mobile Parity Wave — Truth Discovery & Remediation

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Date:** 2026-06-11  
**Scope:** Citizen + Provider Expo apps under `apps/mobile/`

## What was found

| Area | Finding |
|------|---------|
| Framework | Expo SDK 54, React Native 0.81, React 19, pnpm workspaces |
| App split | `citizen-app` + `provider-app` + 11 shared packages |
| Navigation | Custom Zustand + design-system `TabBar` (not React Navigation stacks) |
| Auth | Keycloak PKCE via `@impilo/mobile-auth`; provider facility/workspace gates |
| API | BFF-only via `@impilo/mobile-api-client` (`/internal/v1/...`) |
| Registry | BFF launcher + hardcoded section/tab lists; **no** canonical service registry |
| Gaps | Simba not named in code; Costa pending charges dead; scattered service lists |
| Guards | `check-mobile-parity.sh` existed; no registry/wiring/no-mock node guards |
| Build | Per-app `tsc` + vitest; no root mobile scripts; pnpm not on PATH (use `npx pnpm`) |

## What was broken / missing

- No `@impilo/mobile-registry` package
- Dashboard service cards not registry-driven
- Provider tab order buried Profile; Launch not labelled Work
- Preview env defaulted to LAN IP not `41.57.127.235`
- Missing `ServiceCard`, `OfflineBanner`, `UnauthorizedState`, `DashboardSection`
- Missing implementation docs under `docs/implementation/`

## What was fixed

1. **`@impilo/mobile-registry`** — 21 canonical services + wiring metadata
2. **Design system** — ServiceCard, ServiceStatusBadge, DashboardSection, OfflineBanner, UnauthorizedState, PrimaryActionCard
3. **Citizen Home** — registry-backed service hub (`citizen-service-hub`)
4. **Provider Work dashboard** — registry-backed service hub (`provider-service-hub`)
5. **Provider tabs** — Work + My Professional prioritised; Training tab for Fundo/Apps
6. **Guards** — `mobile-service-parity.mjs`, `mobile-service-wiring.mjs`, `mobile-no-mocks.mjs`
7. **API** — `safeApi` wrapper in mobile-api-client
8. **Preview config** — `EXPO_PUBLIC_*` + preview variant defaults in `app.config.ts`
9. **Documentation** — parity wave, wiring matrix, build-run, runtime smoke

## What remains blocked

- Costa pending charges / quotes (BFF route missing)
- PACS native viewer (deep-link handoff)
- Full OROS orders hub screen
- Direct Tshepo/Butano FHIR clients
- EAS APK/iOS cloud builds (advisory on VM without device farm)

## Commands

```bash
cd /opt/impilo/repos/Impilo-vNext/apps/mobile
npx pnpm@9 install --no-frozen-lockfile
npx pnpm@9 mobile:typecheck
npx pnpm@9 mobile:test
npx pnpm@9 guard:mobile-parity
npx pnpm@9 guard:mobile-wiring

# Dev run
cd citizen-app && npx pnpm@9 start
cd provider-app && npx pnpm@9 start

# Preview variant
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 npx pnpm@9 start
```

## Files changed (wave)

- `apps/mobile/packages/mobile-registry/**` (new)
- `apps/mobile/packages/mobile-design-system/src/components/ServiceCard.tsx` (new)
- `apps/mobile/packages/mobile-design-system/src/**` (exports + feedback/layout)
- `apps/mobile/citizen-app/src/navigation/citizenServiceNavigation.ts` (new)
- `apps/mobile/citizen-app/src/screens/HomeScreen.tsx`
- `apps/mobile/provider-app/src/navigation/providerServiceNavigation.ts` (new)
- `apps/mobile/provider-app/src/navigation/ProviderTabs.tsx`
- `apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx`
- `apps/mobile/packages/mobile-api-client/src/safeApi.ts` (new)
- `apps/mobile/citizen-app/app.config.ts`, `provider-app/app.config.ts`
- `apps/mobile/package.json`, `citizen-app/package.json`, `provider-app/package.json`
- `scripts/guard/mobile-*.mjs` (new)
- `docs/implementation/mobile-*.md` (new)
