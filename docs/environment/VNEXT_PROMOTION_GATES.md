# vNext Promotion Gates

Promotion criteria between pipeline environments. Names align with [`VNEXT_ENVIRONMENT_LADDER.md`](./VNEXT_ENVIRONMENT_LADDER.md).

## Static → runtime (mobile)

Mobile work cannot move from **static/code closure** to **runtime/mobile preview closure** until **impilo-mobile-android-sandbox** passes:

| Gate | Evidence |
|------|----------|
| Emulator readiness | `reports/mobile/android-emulator-readiness.md` |
| KVM validated | `reports/mobile/maestro-vm-activation.md` |
| Citizen runtime smoke | `reports/mobile/citizen-runtime-smoke.md` |
| Provider runtime smoke | `reports/mobile/provider-runtime-smoke.md` |
| Mobile no-mock/no-stub | `pnpm guard:mobile-parity` / no-stubs (412 files baseline) |
| Mobile parity/wiring | `guard:mobile-parity`, service wiring guards |
| APK/build readiness | Debug APK install OR documented blocker in closure report |
| Maestro flows (if configured) | `scripts/mobile/verify-maestro-flows.sh` |
| Closure summary | `reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md` |

**API target for runtime gates:** `http://41.57.127.235` unless explicitly overridden.

## Web preview deploy (235)

| Gate | Evidence |
|------|----------|
| VM quality gates | `reports/pipeline/latest-summary.json` PASSED at HEAD |
| User authorization | Manual deploy phrase per deploy script |
| Post-deploy smoke | `/health/version` commit match |

## Static mobile closure (achieved — reference)

Mobile Closure Wave commit `46254765` (prior `0ce94f82`):

- Citizen typecheck: PASS · Provider typecheck: PASS
- Citizen tests: PASS (122) · Provider tests: PASS (123) · Registry: PASS (4)
- Service parity / wiring / no-mocks / combined guard:mobile-parity: PASS
- Costa citizen: truthfully blocked (no citizen BFF route)
- Runtime smoke: **NOT RUN** — Maestro VM purpose is to close this gap

See [`docs/implementation/mobile-closure-wave.md`](../implementation/mobile-closure-wave.md).

## Future environments

Full integration, production simulation, staging, and production each require separate gate packs — not implemented on 218 or 235 today.
