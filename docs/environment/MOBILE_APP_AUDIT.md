# Mobile App Audit

## Structure

| Item | Path |
|------|------|
| Workspace root | `apps/mobile/package.json` (pnpm workspace) |
| Citizen app | `apps/mobile/citizen-app/` — Expo (`app.json`, `eas.json`, `metro.config.js`) |
| Provider app | `apps/mobile/provider-app/` — Expo |
| Shared packages | `apps/mobile/packages/mobile-*` (auth, api-client, trust, timeline, ndila, etc.) |

**Count:** Two apps + shared packages (not a single binary shell).

## Stack

- **Framework:** React Native / Expo
- **Package manager:** pnpm (workspace)
- **Build:** EAS (`eas.json` per app)
- **Android:** Configured via Expo/EAS (no root `gradlew` in shallow find — native projects generated at build time)
- **iOS:** Requires macOS runner + Apple certificates for production/TestFlight

## Preview backend

- API client package: `apps/mobile/packages/mobile-api-client`
- Preview URL should target BFF/gateway at preview host — verify env in app config / EAS profiles before release testing.

## CI / gates

- Local: `bash scripts/test/run-mobile-checks.sh` (install, lint, test if scripts exist)
- Status: **advisory** in `run-preview-gates.sh` until Android preview APK pipeline stabilizes

## Gaps

- No verified iOS build on VM
- Full APK build not blocking CI yet
- Deep mobile E2E (Maestro) separate from preview HTTP regression
