# Testing and Smoke Checks

## Executed in this sweep

### Backend

- `mvn -DskipTests package` from `services`
- Multiple resume builds (`-rf`) to isolate/fix failing modules

### Web

- `npm run type-check` from `ui`
- `npm run lint` from `ui`
- `npm run build` from `ui`
- Targeted app builds used during remediation (`ops-console`, `msika-web`, `support-console`, `pharmacy-web`, `developer-console`)

### Mobile

- `pnpm -r type-check` from `apps/mobile`
- `pnpm -r test` from `apps/mobile`

## Added/updated smoke coverage in this sweep

- Added `apps/mobile/packages/mobile-ndila/test/exports.test.ts` to ensure workspace test suite no longer fails due missing test files.

## Recommended next smoke suite (pipeline-ready)

1. Backend service health endpoints for top-tier modules (Tshepo, registries, BFF, Nhume, Ndila, Integration Hub, Comms Hub, Telehealth).
2. API route smoke checks through `experience-bff`.
3. Mobile app boot checks (citizen/provider) in CI.
4. Web app route smoke for role-critical dashboards.
5. Trust-layer header propagation smoke checks.
6. Nompilo provider fallback smoke checks.
7. Integration adapter registry and connector availability checks.

## Known test execution caveats

- Full integration tests requiring provisioned infra were not run in this pass.
- `-DskipTests` backend packaging was used to prioritize compile/runtime stabilization.
