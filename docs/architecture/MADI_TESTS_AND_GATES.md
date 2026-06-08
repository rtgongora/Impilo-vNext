# MADI Tests and Quality Gates

## Backend

| Gate | Location |
|------|----------|
| Golden contract IT | `services/madi-service/.../MadiGoldenContractIT.java` |
| Unit tests | `DonorServiceTest`, `BloodOrderServiceTest`, `TransfusionServiceTest`, `BloodUnitServiceTest` |

Run:

```bash
cd services/madi-service && ./mvnw test
```

## Mobile

| Gate | Location |
|------|----------|
| Citizen `madiService` unit tests | `apps/mobile/citizen-app/src/services/madiService.test.ts` |
| Provider `madiService` unit tests | `apps/mobile/provider-app/src/services/madiService.test.ts` |

Run:

```bash
cd apps/mobile/citizen-app && pnpm exec vitest run src/services/madiService.test.ts
cd apps/mobile/provider-app && pnpm exec vitest run src/services/madiService.test.ts
```

Or full mobile suite:

```bash
cd apps/mobile && pnpm test
```

## Parity documentation gates

| Script | Purpose |
|--------|---------|
| `scripts/frontend/generate-parity-docs.mjs` | Embeds MADI CAPABILITIES entries (9 capabilities) |
| `scripts/guard/check-mobile-parity.sh` | Repository mobile parity guard (when BFF routes registered) |
| `scripts/guard/check-backend-frontend-parity.sh` | Backend–frontend alignment |

Regenerate parity docs:

```bash
node scripts/frontend/generate-parity-docs.mjs
```

## Core transaction contract

Blood-related types added to `contracts/core-transaction.ts`:

- `BLOOD_DONATION`
- `BLOOD_ORDER`
- `TRANSFUSION`
- `HAEMOVIGILANCE`

## CI / VM quality gates

Before preview deploy, run on VM:

```bash
bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/pipeline/cursor-local-feedback.sh
```

## Acceptance checklist (mobile wave)

- [x] Citizen hub reachable from Personal → Blood Donor (`MadiDonorHubScreen`, `/internal/v1/mobile/citizen/madi/*`)
- [x] Provider tools reachable from Clinical Tools MADI tabs (`MadiProviderHubScreen`, provider `madiService`)
- [x] `madiService` tests pass in both apps (`citizen-app` + `provider-app` vitest)
- [x] BFF mobile routes proxy to `madi-service` (`CitizenMadiController`, `ProviderMadiController`)
- [x] No production mocks on MADI mobile paths (real BFF proxy; graceful offline sync only on drives)
- [x] Trust headers present on all mobile API calls (`mobile-api-client` companion headers)

### Intentionally web-only (not mobile parity gaps)

| Capability | Rationale |
|------------|-----------|
| Blood processing / ZIBO pins | Lab workstation; jurisdiction terminology admin |
| Central bank emergency redistribution | National ops console |
| National haemovigilance dashboard | Aggregated national surveillance view |

## Golden-thread E2E

| Layer | Evidence |
|-------|----------|
| Web unit | `ui/one-ui-shell/src/lib/__tests__/madi-golden-thread.test.ts` |
| Playwright | `ui/one-ui-shell/e2e/madi-flow.spec.ts` |
| BFF | `MadiControllerTest.java` |
| Backend | `DonorPreScreeningTest`, `BloodOrderServiceTest`, `TransfusionPreVerifyTest`, `HaemovigilanceServiceTest` |
| Core transaction | `COMPLETION_EVIDENCE` entries: `blood-donation`, `blood-order`, `transfusion-episode`, `haemovigilance-report` |

Run Playwright (with dev server or `PLAYWRIGHT_SKIP_WEBSERVER=1` against preview):

```bash
cd ui/one-ui-shell && npm run e2e -- e2e/madi-flow.spec.ts
```

## Runtime integration smoke (preview)

After authorized preview deploy:

```bash
bash scripts/test/smoke-madi-preview-integration.sh
```

Records live vs graceful-skip for NHUME handoff surfaces, surveillance-tuned forecast, and national haemovigilance when optional services are up.

## Full-boot validation

`madi-service` is listed in `config/full-boot-waves.yml`. Full-boot preview (`impilo-full-preview`) requires `FULL_BOOT_PASS` from `scripts/guard/check-full-boot-runtime-completeness.sh` before `AUTHORIZE FULL BOOT PREVIEW DEPLOY`. MADI is validated as part of the clinical wave when the full stack boots.

## Gap-closure wave (2026-06)

| Capability | Surface |
|------------|---------|
| Nompilo donor assist + pre-screening | Web `/madi/donor/screening`, citizen `DonorScreeningScreen`, BFF `MadiDonorAssistController` |
| Bedside biometric/barcode verify | Web `MadiBedsideVerifyPanel`, mobile `MadiTransfusionScreen` pre-verify |
| IoT fridge monitoring | Web `/madi/blood-bank/fridges`, `IotIntegration` → telemetry service |
| National haemovigilance | Web `/madi/haemovigilance/national` |
| OROS deep-link | Web `/madi/orders/[orderId]` → `/lab?orderId=` |
| Offline drive conflicts | Provider `MadiDriveCaptureScreen` + sync-conflicts API |

## Remaining honest gaps (lower priority)

Maturity gaps (2026-06 follow-up) — implemented:

| Capability | Surface |
|------------|---------|
| NHUME physical handoff | Auto on emergency approve/fulfil → `NhumeIntegration.createBloodRedistributionDelivery`; retry via `POST .../handoff`; track at `/nhume/deliveries/[id]` |
| Epidemiology-tuned forecast | `SurveillanceIntegration` pulls counters/alerts; multiplier on `/madi/dashboard` forecast table |
| ZIBO jurisdiction pins | `config/madi/zibo-jurisdiction-pins.yaml` + `GET /madi/terminology/component-pins`; processing UI jurisdiction selector |
