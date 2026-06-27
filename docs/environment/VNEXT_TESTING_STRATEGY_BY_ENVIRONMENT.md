# vNext Testing Strategy by Environment

Maps validation types to pipeline environments. See [`VNEXT_ENVIRONMENT_LADDER.md`](./VNEXT_ENVIRONMENT_LADDER.md).

## impilo-web-preview (235) — active

| Test | Script / location |
|------|-------------------|
| VM quality gates | `scripts/pipeline/run-local-quality-gates.sh` |
| Backend–frontend parity | `scripts/guard/check-backend-frontend-parity.sh` |
| HTTP preview regression | `tests/regression/preview-http-regression.sh` |
| Playwright E2E | `ui/one-ui-shell` (`npm run e2e`) |
| Route / no-stubs guards | `npm run test:routes`, `npm run test:no-stubs` |
| Preview deploy smoke | `scripts/deploy/preview-smoke-test.sh` |
| Mobile **static** gates | `apps/mobile` → `pnpm mobile:typecheck`, `pnpm mobile:test`, `pnpm guard:mobile-parity` |

**Does not run here:** Android emulator, Maestro runtime smoke.

## impilo-mobile-android-sandbox (218) — active

| Test | Script / location |
|------|-------------------|
| KVM readiness | `scripts/mobile/verify-maestro-vm-kvm.sh` |
| Android prebuild / APK | Expo prebuild + Gradle on 218 only |
| Maestro citizen/provider smoke | `scripts/mobile/verify-maestro-flows.sh` |
| Runtime smoke checklist | `docs/implementation/mobile-runtime-smoke.md` |
| Runtime reports | `reports/mobile/*` (commit back via Git) |

**API target:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`

**Does not run here:** Backend deploy, k3s, web Playwright at scale.

## impilo-mobile-preview-control — planned

Mobile preview routing, variant matrix, handoff validation between 235 API and sandboxes.

## impilo-mobile-ios-sandbox / EAS — planned

EAS Build smoke, iOS simulator/TestFlight path — not Ubuntu native iOS builds.

## impilo-web-test-sandbox — planned

Dedicated web regression beyond preview slice.

## impilo-cross-surface-test-controller — planned

Golden-thread journeys spanning web shell + mobile apps + BFF.

## impilo-full-integration-sandbox — planned

Full-boot completeness (`scripts/guard/check-full-boot-runtime-completeness.sh`), all classified services.

## impilo-production-simulation-lab — planned

Load, chaos, ops drills — not Maestro VM.

## impilo-staging / impilo-production — planned

Formal promotion gates per [`VNEXT_PROMOTION_GATES.md`](./VNEXT_PROMOTION_GATES.md).

## GitHub Actions

When runners healthy, CI mirrors VM scripts (`.github/workflows/ci.yml`). VM local pipeline remains canonical when CI is infra-blocked. See [`DUAL_MODE_TEST_PIPELINE.md`](./DUAL_MODE_TEST_PIPELINE.md).
