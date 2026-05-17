# Impilo vNext Production Readiness Audit

Last updated: 2026-05-17
Branch: `claude/staging-ux-orchestration-remediation-Yypyl`

## Scope

This pass focused on:
- Monorepo buildability and compile/runtime blockers.
- Backend/web/mobile wiring regression fixes introduced in recent staging work.
- Trust-context header propagation stability in `experience-bff`.
- Production-readiness documentation and doctrine traceability artifacts.

## Commands Executed

### Backend (Java/Spring)

- `mvn -DskipTests package` (from `services`)
- `mvn -DskipTests package -rf :butano-service`
- `mvn -DskipTests package -rf :data-pipeline-service`
- `mvn -DskipTests package -rf :experience-bff`

### Web (UI Turbo workspaces)

- `npm run type-check` (from `ui`)
- `npm run lint` (from `ui`)
- `npm run build` (from `ui`)
- Targeted recovery builds:
  - `npm run build` in `ui/ops-console`
  - `npm run build` in `ui/msika-web`
  - `npm run build` in `ui/support-console`
  - `npm run build` in `ui/pharmacy-web`
  - `npm run build` in `ui/developer-console`

### Mobile (PNPM workspaces)

- `pnpm install` (from `apps/mobile`)
- `pnpm -r type-check`
- `pnpm -r test`

## Key Breakages Found and Repaired

1. Backend compile break in `butano-service` due invalid FHIR status enum reference.
2. Backend compile/test-compile drift in `data-pipeline-service` (`OutboxPublisher` constructor mismatch in tests).
3. Backend compile break cluster in `experience-bff`:
   - Invalid `ObjectMapper` FQCN usage in social controllers.
   - Duplicate method signature in `PublicHealthController`.
   - Stale `CompanionHeaders` constants unavailable in current dependency version.
4. Web build blockers:
   - Missing root app layout in `ops-console`.
   - Next.js prerender errors where `useSearchParams()` lacked Suspense boundaries (`msika-web`, `support-console`, `pharmacy-web`, `developer-console`).
5. Mobile build/test blockers:
   - JSX parse error in provider `MarketplaceOpsScreen`.
   - `Button` API mismatches in citizen/provider Health OS screens.
   - Invalid store selector usage in provider marketplace screen.
   - Duplicate style key in provider social screen.
   - `mobile-ndila` package had no tests and failed workspace test run.

## Current Build Readiness State

- Backend compile/package: **Passes with `-DskipTests` across full reactor**.
- Web workspaces type-check/lint/build: **Pass** (warnings remain in tailwind content globs and some lint warnings not escalated to errors).
- Mobile workspace type-check: **Pass**.
- Mobile workspace tests: **Pass**.

## Notable Non-Blocking Warnings

- Multiple web apps warn about Tailwind content patterns that may overmatch and slow builds.
- Some Next workspaces show mixed `@next/swc` version warnings (14.2.15 vs 14.2.18), but builds complete.
- Mobile tests output Vite CJS deprecation warnings.

## Production Preparation Outcome

This branch is materially closer to production-preparation readiness:
- High-impact compile/build blockers removed.
- Social/community and mobile parity changes are now build-safe.
- Trust header forwarding in BFF no longer depends on unavailable constants for extension headers.
- Repo-specific production-readiness artifacts now exist for operations and handoff.
