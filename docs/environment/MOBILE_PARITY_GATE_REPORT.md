# Mobile parity gate report

**Date:** 2026-05-31

## Apps discovered

| App | Path | Role |
|-----|------|------|
| Citizen | `apps/mobile/citizen-app` | Person / wellness / health |
| Provider | `apps/mobile/provider-app` | Clinical / ops / field |

**Stack:** Expo, pnpm, shared `mobile-*` packages.

## Android / iOS

| Platform | Status |
|----------|--------|
| Android | EAS build path; advisory full APK in CI |
| iOS | Advisory until macOS/TestFlight |

## Parity summary

| Status | Notes |
|--------|-------|
| complete (`yes`) | Social, some domains |
| partial | Majority of registry rows |
| missing / deferred | UBOMI, some admin-only |
| platform-limited | ZIBO n/a on mobile |

## Gate status

| Item | Status |
|------|--------|
| `check-mobile-parity.sh` | Implemented (master) |
| `check-mobile-mocks-and-stubs.sh` | New |
| `check-mobile-api-surfacing.sh` | New |
| `MOBILE_PARITY_MATRIX.md` | Generated from registry |
| CI job `Mobile Parity Gate` | Added |
| VM pipeline phase | `parity-mobile` blocking |

## Immediate priorities

1. Deepen partial mobile journeys (core transaction, Fundo, field public health).
2. Keep demoJourneyService dev-only (allowlisted).
3. Wire citizen BUTANO personal sections per matrix.
