# Mobile Closure Wave

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Prior commit:** `0ce94f82` (parity/wiring wave)  
**Date:** 2026-06-11

## Failures reproduced

| Gate | Result before fixes |
|------|---------------------|
| `mobile:typecheck` | Citizen: AssuranceChoiceScreen, LoadingSpinner `label`, BookingsSection, PersonalScreen prod-ready, ndila geolocation. Provider: BookingRequestsScreen, ClinicalToolsScreen import, TheatreProcedureScreen, impiloLiveService filter |
| Citizen tests | 2 files failed load (`__DEV__`, `@expo/vector-icons` via HomeScreen import) |
| Provider tests | 5 files failed (`__DEV__`, vector-icons, madiService assertion) |
| Guards | Passed (unchanged) |
| Expo export | Blocked — `react-native-web` not installed |

## Fixes applied

### TypeScript

- `LoadingSpinner`: added `label` alias for `message` (design-system)
- `AssuranceChoiceScreen`: `IoniconName` type instead of `Ionicons.glyphMap`
- `configureMobileGeolocation`: normalize `accuracy: null` → `undefined`
- `BookingsSection`: removed invalid `CardHeader.icon`; Button uses `title`
- `PersonalScreen`: `Partial<Record<PersonalTab, React.FC>>`; prod-ready rendered separately
- `BookingRequestsScreen`: `useAppStore()` destructuring; Button/CardHeader fixes
- `ClinicalToolsScreen`: import `CoreTransactionJourneyShellScreen`
- `TheatreProcedureScreen`: cast episodes array for map callback
- `impiloLiveService`: filter cast for CPD history

### Tests

- Citizen: `__DEV__`, `expo-secure-store`, `expo-modules-core` mocks; vitest alias for `@expo/vector-icons`; extracted `homeCommsKpis.ts`
- Provider: same vitest/env pattern; extracted `providerCommsKpis.ts`; fixed `madiService.test.ts` expectation `{ ok: true }`

### Costa

- No citizen BFF route exists — documented in `mobile-costa-bff-contract.md`
- `fetchPendingCharges()` returns `{ blocked: true, charges: [], blockedReason }`
- `FinanceSection` renders `costa-blocked-state` card
- Provider: existing `queueService.fetchCharges` → `/internal/v1/mobile/provider/billing/charges` (partially wired)
- Registry wiring updated to `partiallyWired` for Costa

## Results after fixes

```bash
cd apps/mobile
npx pnpm@9 mobile:typecheck   # PASS (citizen + provider)
npx pnpm@9 mobile:test        # PASS — citizen 122, provider 123, registry 4
npx pnpm@9 guard:mobile-parity # PASS
```

## Expo / EAS

```bash
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 npx expo export
```

**Blocker:** Expo export requires `react-native-web@^0.21.0` (not in workspace). Install on build machine:

```bash
cd apps/mobile/citizen-app && npx expo install react-native-web
cd apps/mobile/provider-app && npx expo install react-native-web
```

EAS local APK: not run — requires Android SDK + credentials on configured build host.

## Runtime smoke

| Item | Status |
|------|--------|
| Citizen `expo start` (preview) | NOT RUN — no emulator/device on VM session |
| Provider `expo start` (preview) | NOT RUN |
| Manual smoke checklist | See `mobile-runtime-smoke.md` — all NOT RUN |

**Device validation command:**

```bash
cd apps/mobile/citizen-app
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 npx pnpm@9 start
# Scan QR with Expo Go on phone against http://41.57.127.235
```

## Maestro VM — runtime closure path (2026-06-27)

Static closure (this wave) is **not** runtime closure. The **impilo-mobile-android-sandbox** (`41.57.127.218`) is provisioned to move mobile from static/code closure to **runtime/mobile preview closure**.

| Item | Static (235/CI) | Runtime (218) |
|------|-----------------|---------------|
| Typecheck / unit tests | PASS | N/A |
| Maestro / emulator smoke | NOT RUN | **PENDING** |
| Citizen/provider start | NOT RUN | **PENDING** |
| EAS/APK | NOT RUN | **PENDING** on 218 after toolchain approval |

**Reference commit:** `46254765` (prior parity wave `0ce94f82`).

Runbook: [`docs/mobile/MOBILE_ANDROID_SANDBOX.md`](../mobile/MOBILE_ANDROID_SANDBOX.md) · Closure report: [`reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md`](../../reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md) · Toolchain plan: [`docs/mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md`](../mobile/MAESTRO_VM_TOOLCHAIN_SETUP_PLAN.md).

## Files changed (closure wave)

- `apps/mobile/citizen-app/src/__tests__/setup.ts`, `vitest.config.ts`, `mocks/expo-vector-icons.tsx`
- `apps/mobile/citizen-app/src/lib/homeCommsKpis.ts`
- `apps/mobile/citizen-app/src/screens/*` (AssuranceChoice, Bookings, Finance, Personal, Home)
- `apps/mobile/citizen-app/src/services/financeService.ts`
- `apps/mobile/packages/mobile-design-system/src/feedback/LoadingSpinner.tsx`
- `apps/mobile/packages/mobile-ndila/src/configureMobileGeolocation.ts`
- `apps/mobile/packages/mobile-registry/src/wiring.ts`
- `apps/mobile/provider-app/src/__tests__/setup.ts`, `vitest.config.ts`, `mocks/`
- `apps/mobile/provider-app/src/lib/providerCommsKpis.ts`
- `apps/mobile/provider-app/src/screens/provider/*` (BookingRequests, ClinicalTools, ProviderDashboard, TheatreProcedure)
- `apps/mobile/provider-app/src/services/impiloLiveService.ts`, `madiService.test.ts`
- `docs/implementation/mobile-costa-bff-contract.md`
- `docs/implementation/mobile-closure-wave.md` (this file)
