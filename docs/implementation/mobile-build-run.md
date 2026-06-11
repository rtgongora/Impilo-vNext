# Mobile Build & Run

## Prerequisites

- Node ≥ 20 on VM (`robert@41.57.127.235`)
- `npx pnpm@9` (pnpm not globally installed on VM)

## Install

```bash
cd /opt/impilo/repos/Impilo-vNext/apps/mobile
npx pnpm@9 install --no-frozen-lockfile
```

## Static quality

```bash
npx pnpm@9 mobile:lint        # eslint per app
npx pnpm@9 mobile:typecheck   # tsc --noEmit citizen + provider
npx pnpm@9 mobile:test        # vitest citizen + provider + mobile-registry
npx pnpm@9 guard:mobile-parity
npx pnpm@9 guard:mobile-wiring
```

## Dev run (Expo)

```bash
# Citizen
cd /opt/impilo/repos/Impilo-vNext/apps/mobile/citizen-app
npx pnpm@9 start

# Provider
cd /opt/impilo/repos/Impilo-vNext/apps/mobile/provider-app
npx pnpm@9 start
```

## Preview backend (k3s sandbox)

```bash
export EXPO_PUBLIC_APP_VARIANT=preview
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
export EXPO_PUBLIC_AUTH_BASE_URL=http://41.57.127.235:8480
export EXPO_PUBLIC_WEB_BASE_URL=http://41.57.127.235
npx pnpm@9 start
```

## Build targets

| Target | Command | Notes |
|--------|---------|-------|
| Typecheck | `npx pnpm@9 mobile:build` | Blocking static build gate |
| EAS Android | `cd citizen-app && npx pnpm@9 build:android` | Requires EAS credentials |
| EAS iOS | `cd citizen-app && npx pnpm@9 build:ios` | Requires Apple credentials |
| Expo export | `npx expo export` | Bundle export when native toolchain available |

## Architecture summary

- **Citizen:** 7 tabs — Home, Health, Feed, Services, Messages, Care, Public
- **Provider:** modes — provider (Work-first tabs), outreach, supervisor, offline, courier
- **Shared packages:** api-client, auth, design-system, registry, nompilo, ndila, offline, trust, timeline, messaging, integration

## Auth / context

- Citizen: Health ID via Keycloak `citizen-app` client → VITO actor headers
- Provider: Provider ID → VARAPI profile → facility (Tuso) → workspace → TSHEPO trust headers on every BFF call
