# App Platform Audit

**Date:** 2026-03-15
**Branch:** `claude/review-project-manifest-jb5O0`
**Auditor:** Claude Code (automated repo inspection)

## Summary

Four apps were requested for audit: Provider App, Citizen/Patient App, Support App, and Developer/Partner App. Two of these (Provider App and Citizen App) exist as mobile app projects under `apps/mobile/`. The other two (Support App and Developer/Partner App) exist as **web-only** console applications under `ui/` — they are not mobile apps.

## Audit Table

| App | Path | Framework | Languages | Android | iOS | Evidence | Readiness |
|-----|------|-----------|-----------|---------|-----|----------|-----------|
| Provider App | `apps/mobile/provider-app/` | Expo (React Native) | TypeScript, TSX | NO | NO | `package.json` scripts use `expo start` / `expo export`; config uses `EXPO_PUBLIC_*` env vars; `react-native >=0.73.0` peer dep in shared `mobile-design-system`. **No `android/` or `ios/` directories exist.** App.tsx uses `window.addEventListener` and HTML `div` elements, not React Native components. | Scaffolded only. App shell exists with screens, navigation, auth, offline support, and Zustand stores, but it runs as a web-only Expo app. No native platform directories, no native modules, no app.json/app.config.js for Expo managed workflow. Not production-ready for mobile deployment. |
| Citizen / Patient App | `apps/mobile/citizen-app/` | Expo (React Native) | TypeScript, TSX | NO | NO | `package.json` scripts use `expo start` / `expo export`; config uses `EXPO_PUBLIC_*` env vars; `react-native >=0.73.0` peer dep in shared `mobile-design-system`. **No `android/` or `ios/` directories exist.** App.tsx uses `window.addEventListener` and HTML `div` elements, not React Native components. | Scaffolded only. App shell exists with screens (personal, social, marketplace, messaging, telehealth), navigation, auth, and Zustand stores, but it runs as a web-only Expo app. No native platform directories, no native modules, no app.json/app.config.js. Not production-ready for mobile deployment. |
| Support App | `ui/support-console/` | Next.js 14.2 (web) | TypeScript, TSX | NO | NO | `package.json` declares `next 14.2.18`, `react-dom`, `tailwindcss`, `@tanstack/react-query`. Runs on port 3006. Entry point: `ui/support-console/src/app/layout.tsx`. No mobile framework dependencies. Documented in `docs/apps/support-app/README.md` as "operator-facing web application". | This is a **web application**, not a mobile app. It has no mobile build targets, no React Native or Expo dependencies, and no native platform support. The question of Android/iOS does not apply. |
| Developer / Partner App | `ui/developer-console/` | Next.js 14.2 (web) | TypeScript, TSX | NO | NO | `package.json` declares `next 14.2.18`, `react-dom`, `tailwindcss`, `@tanstack/react-query`. Runs on port 3007. Entry point: `ui/developer-console/src/app/(developer)/dashboard/page.tsx`. No mobile framework dependencies. Documented in `docs/apps/developer-partner-app/README.md` as "operator-facing web application". | This is a **web application**, not a mobile app. It has no mobile build targets, no React Native or Expo dependencies, and no native platform support. The question of Android/iOS does not apply. |

## Detailed Evidence

### Provider App (`apps/mobile/provider-app/`)

- **`package.json`**: Scripts declare `"dev": "expo start"` and `"build": "expo export"`, confirming Expo as the intended build system. Dependencies include `react ^18.3.1`, `zustand ^4.5.0`, and seven `@impilo/mobile-*` workspace packages. Notably, `expo` itself is **not listed as a dependency** — only referenced in scripts.
- **`src/config.ts`**: Uses `process.env.EXPO_PUBLIC_*` environment variables for Keycloak URL, realm, client ID, API base URL, and redirect URI (`impilo.provider://callback`).
- **`src/App.tsx`**: Uses `window.addEventListener("online", ...)` and renders HTML `div` elements via `React.createElement("div", ...)` — these are web DOM APIs, not React Native components (`View`, `Text`, etc.).
- **No `android/` or `ios/` directory**: `find apps -type d \( -name android -o -name ios \)` returned empty.
- **No `app.json` or `app.config.js`**: No Expo configuration file exists.
- **Screens**: `provider/`, `outreach/`, `supervisor/`, `offline/` — four clinical workflow modes are scaffolded with navigation tabs.
- **Shared package `mobile-design-system`**: Declares `"react-native": ">=0.73.0"` as a peer dependency, but the actual app code does not use React Native components.

### Citizen / Patient App (`apps/mobile/citizen-app/`)

- **`package.json`**: Identical structure to Provider App — Expo in scripts, same workspace dependencies, same React/Zustand versions. `expo` is not a listed dependency.
- **`src/config.ts`**: Uses `EXPO_PUBLIC_*` env vars with client ID `citizen-app` and redirect URI `impilo.citizen://callback`.
- **`src/App.tsx`**: Same web DOM pattern — `window.addEventListener`, HTML `div` rendering.
- **No `android/` or `ios/` directory**.
- **No `app.json` or `app.config.js`**.
- **Screens**: `personal/`, `social/`, `marketplace/`, `messaging/`, `telehealth/` — five citizen-facing domains are scaffolded.

### Support App (`ui/support-console/`)

- **`package.json`**: Next.js 14.2.18 with `react-dom`, TailwindCSS, TanStack Query, Zustand. Standard web stack.
- **Documentation** (`docs/apps/support-app/README.md`): Explicitly described as "an operator-facing web application" for helpdesk and incident management.
- **No mobile framework references**: No Expo, React Native, Capacitor, Ionic, or Flutter dependencies.

### Developer / Partner App (`ui/developer-console/`)

- **`package.json`**: Next.js 14.2.18 with `react-dom`, TailwindCSS, TanStack Query, Zustand. Standard web stack.
- **Documentation** (`docs/apps/developer-partner-app/README.md`): Described as "operator-facing web application" for partner onboarding, API key management, and sandbox testing.
- **No mobile framework references**: No Expo, React Native, Capacitor, Ionic, or Flutter dependencies.

## Shared Mobile Packages

Seven shared packages exist under `apps/mobile/packages/`:

| Package | Purpose |
|---------|---------|
| `@impilo/mobile-trust` | Trust header injection for mobile API calls |
| `@impilo/mobile-auth` | Keycloak authentication, token management, secure storage |
| `@impilo/mobile-api-client` | HTTP client with retry, timeout, step-up challenge support |
| `@impilo/mobile-messaging` | Push notification and device registration |
| `@impilo/mobile-timeline` | Clinical timeline rendering |
| `@impilo/mobile-offline` | Offline queue and storage adapters |
| `@impilo/mobile-design-system` | Shared UI components and design tokens (peer dep on `react-native >=0.73.0`) |

## Conclusions

1. **No app in this repository currently targets Android or iOS for native deployment.** The two mobile apps (Provider and Citizen) reference Expo in their build scripts and use `EXPO_PUBLIC_*` env vars, but lack the `expo` dependency, `app.json` configuration, and native platform directories (`android/`, `ios/`) required for actual mobile builds.
2. **The mobile apps are scaffolded but not production-ready.** They have working app shells with screens, navigation, auth, and state management, but use web DOM APIs (`window`, `div`) instead of React Native components, indicating the current code runs only in a browser context.
3. **Support App and Developer/Partner App are web-only.** They are Next.js applications with no mobile aspirations in their current implementation.
