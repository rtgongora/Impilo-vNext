# App Runtime Results

## Summary
| App | Target Posture | Build Attempted | Runtime Attempted | Result | Blocker |
|-----|---------------|----------------|-------------------|--------|---------|
| Experience UI (ui/experience) | Web (Next.js 14) | Yes — npm install + next build | No (Docker) | **BUILD PASS** | Runtime needs Docker |
| Support Console (ui/support-console) | Web (Next.js 14) | Yes — npm install + tsc | No | BUILD FAIL | workspace:* protocol — needs pnpm |
| Developer Console (ui/developer-console) | Web (Next.js 14) | Yes — npm install + tsc | No | BUILD FAIL | workspace:* protocol — needs pnpm |
| Provider App (apps/mobile/provider-app) | Android + iOS (Expo/React Native) | Yes — tsc | No | BUILD FAIL | workspace:* + expo base tsconfig |
| Citizen App (apps/mobile/citizen-app) | Android + iOS (Expo/React Native) | Yes — tsc | No | BUILD FAIL | workspace:* + expo base tsconfig |

## Detailed Results

### Experience UI — BUILD PASS
- **Target**: Web application (Next.js 14.2.x)
- **Build tool**: `npm install --legacy-peer-deps && npx next build`
- **Result**: SUCCESS — 80+ routes compiled (static + dynamic)
- **Evidence**: Production build completed with all routes listed. First Load JS: 87.2 kB shared.
- **Runtime**: Needs Docker Compose to serve at port 3020 with BFF backend. Standalone build artifacts exist in `.next/`.
- **Type-check**: Clean (tsc --noEmit passes with no errors)

### Support Console — BUILD FAIL
- **Target**: Web application (Next.js 14.2.x)
- **Build tool**: `npm install --legacy-peer-deps && npx tsc --noEmit`
- **Result**: FAIL — npm install succeeds but tsc fails
- **Error**: `Cannot find module 'vitest'` — test files import vitest which isn't installed
- **Root cause**: `"shared-ui": "workspace:*"` in package.json uses pnpm workspace protocol. Without a root pnpm-workspace.yaml, npm cannot resolve workspace dependencies.
- **Fix**: Create root pnpm-workspace.yaml with `packages: ['ui/*', 'libs/*']` and use `pnpm install` instead of `npm install`.

### Developer Console — BUILD FAIL
- **Target**: Web application (Next.js 14.2.x)
- **Same root cause as Support Console**: workspace:* protocol.

### Provider App — BUILD FAIL
- **Target**: Android and iOS via Expo (~52.0.0) / React Native (~0.76.0)
- **Build tool**: `npm install && npx tsc --noEmit`
- **Result**: FAIL
- **Errors**:
  1. `npm install` rejects `workspace:*` protocol
  2. `tsconfig.json` extends `expo/tsconfig.base` which needs expo package
  3. Various `implicitly has 'any' type` errors (strict mode without full type annotations)
- **Native build path**: `eas build --platform android` / `eas build --platform ios` (requires EAS CLI + Expo account)
- **Evidence**: package.json correctly targets Expo 52, React Native 0.76, has eas.json for EAS Build configuration
- **Posture**: Real React Native app with navigation, offline support, clinical workflows. Build needs workspace manager + Expo environment.

### Citizen App — BUILD FAIL
- **Target**: Android and iOS via Expo (~52.0.0) / React Native (~0.76.0)
- **Same root cause as Provider App**: workspace:* + expo base tsconfig.
- **Evidence**: package.json correctly targets Expo 52, React Native 0.76, has eas.json
- **Posture**: Real React Native app with health, social, marketplace, messaging, telehealth screens.

## Native Mobile Build Evidence

Both mobile apps have:
- `app.config.ts` — Expo app configuration
- `eas.json` — EAS Build configuration for cloud builds
- `metro.config.js` — Metro bundler configuration
- `babel.config.js` — Babel presets for Expo
- Complete navigation structure with @react-navigation
- 7 shared packages in apps/mobile/packages/ (trust, auth, api-client, messaging, timeline, offline, design-system)

Native builds (APK/IPA) require:
1. Expo CLI + EAS CLI installed
2. Expo account with project configured
3. For local builds: Android SDK or Xcode
4. For cloud builds: `eas build --platform android|ios`

This environment cannot run native builds (no Android SDK, no Xcode, no EAS credentials).
