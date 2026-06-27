# Mobile Runtime Sandbox Architecture

How **runtime/mobile preview closure** is achieved on `impilo-mobile-android-sandbox` (218).

## Closure model

| Phase | Where | Status (2026-06-27) |
|-------|-------|---------------------|
| Static / code closure | 235 or CI | **PASS** — Mobile Closure Wave `46254765` |
| Runtime / emulator closure | 218 | **PENDING** — Maestro VM activated, toolchain not installed |

Static closure alone is insufficient for mobile preview readiness. The Maestro VM exists to execute **device-like validation** against the live preview API.

## Runtime validation path

1. **235:** Preview API healthy at `http://41.57.127.235`; quality gates PASS for target commit.
2. **Git:** 218 pulls same branch/commit as 235.
3. **218:** Build/install debug APKs with `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`.
4. **218:** Start KVM emulator; run Maestro flows and manual checklist.
5. **218 → Git → 235:** Commit runtime reports (`reports/mobile/*`).

## Apps under test

| App | Package | Maestro flows |
|-----|---------|---------------|
| Citizen | `zw.gov.impilo.citizen` | `apps/mobile/maestro/flows/citizen-*.yaml` |
| Provider | `zw.gov.impilo.provider` | `apps/mobile/maestro/flows/provider-*.yaml` |

## Truthful blocked states

Runtime validation must preserve truthful blocked UX (e.g. Costa citizen blocked — no citizen BFF route). Do not replace blocked states with fake data.

## Promotion

See [`docs/environment/VNEXT_PROMOTION_GATES.md`](../environment/VNEXT_PROMOTION_GATES.md) — runtime gates before mobile preview promotion.

## Related

- [`MOBILE_ANDROID_SANDBOX.md`](./MOBILE_ANDROID_SANDBOX.md)
- [`docs/implementation/mobile-closure-wave.md`](../implementation/mobile-closure-wave.md)
- [`reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md`](../../reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md)
