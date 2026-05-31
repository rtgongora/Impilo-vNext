# Mobile app inventory

> Updated: 2026-05-31. Regenerate inventories: `node scripts/architecture/generate-parity-inventories.mjs`

## Structure

| Item | Path | Stack |
|------|------|-------|
| Workspace | `apps/mobile/package.json` | pnpm monorepo |
| Citizen app | `apps/mobile/citizen-app/` | Expo / React Native |
| Provider app | `apps/mobile/provider-app/` | Expo / React Native |
| Shared packages | `apps/mobile/packages/mobile-*` | auth, api-client, trust, timeline, ndila, nompilo, offline, design-system |

**Count:** Two apps + shared packages (not a single shell).

## Build & tooling

| Item | Status |
|------|--------|
| Package manager | pnpm |
| Metro | `metro.config.js` per app |
| EAS | `eas.json` per app |
| Android | Expo/EAS build (no root `gradlew` in repo — generated at build) |
| iOS | Requires macOS runner + Apple certs (advisory in CI) |

## API / preview configuration

- BFF access via `@impilo/mobile-api-client` and domain services under `src/services/`.
- Preview testing: configure EAS/env to target preview BFF at `http://41.57.127.235` — see [MOBILE_PREVIEW_TESTING.md](../environment/MOBILE_PREVIEW_TESTING.md).

## Screens (representative)

- **Citizen:** tabs + personal health sections (`apps/mobile/citizen-app/src/screens/`).
- **Provider:** clinical, queue, telemedicine, dispatch, learning, social, registry ops (`apps/mobile/provider-app/src/screens/`).

## Tests

- Package-level tests under `apps/mobile` (run `pnpm test` from workspace).
- Deep Maestro E2E: CI job (advisory when emulator unavailable).

## Parity

See [MOBILE_PARITY_MATRIX.md](./MOBILE_PARITY_MATRIX.md) and `docs/mobile/full-mobile-parity-matrix.md`.
