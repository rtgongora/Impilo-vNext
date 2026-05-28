# Wave 20 — Build / Run Status

> Verified: 2026-05-28 (local Windows dev environment)

## Frontend (one-ui-shell)

| Check | Status | Notes |
|-------|--------|-------|
| `npm run type-check` | **PASS** | tsc --noEmit clean after Wave 20 routes |
| `npm run test:routes` | **PASS** | 417/417 routes |
| `npm run test:no-stubs` | **PASS** | 443 pages scanned |
| `npm test` (vitest) | **PASS** | 579/579 (Slice 4) |
| `npm run e2e` | **PASS** (Slice 11) | 9/9 production-readiness + citizen-life in CI compose job |
| `npm run build` | **PASS** | Next.js production build (Slice 4) |
| Compose UI image | **PASS** | Rebuilt on :3000; Dockerfile fixes (SWC, registry-templates, bookworm-slim) |

## Wave 21 (2026-05-28)

- **Sovereign hardening** — `wait-sovereign-health.mjs`; `up.ps1`/`up.sh` wait loop; CI `e2e-sovereign-smoke`
- **Wellness green** — `WellnessDomainController`; BFF V44 golden-tenant challenge seed
- **Inventory green** — `InventoryController.listRequisitions` demo fallback aligned with V31 seeds
- **Surveillance overlay** — `surveillance-service` in sovereign compose; `PublicHealthFieldTasksController`; V009/V010 migrations
- **Verify script** — `--sovereign`; host-only Ndila/Nhume → SKIP; journey 6 field-tasks when sovereign
- **Flyway** — inventory V002 outbox → V003; surveillance duplicate V003 outbox → V008
- **Docs** — `PRODUCTION_READINESS_REPORT.md`, refreshed gaps/demo/matrix docs

## Wave 20 slice 13 (2026-05-28)

- **Sovereign compose overlay** — `docker-compose.sovereign.yml` (pharmacy :8096, Costa :8101, MusheX :8102, dispatch :8320); `sovereign-rx-db-init`; BFF env overrides to in-network URLs
- **Dev tooling** — `up.ps1 -SovereignHost`, `up.sh --sovereign-host` (Maven builds pharmacy + MusheX + dispatch)
- **Demo seeds** — pharmacy V004, MusheX V007/V008, Costa V011, dispatch V004, BFF V41/V42/V43 `sovereign_demo_anchors`
- **Flyway renumber note** — demo INSERT scripts moved to V004/V007 to avoid conflicting with schema migrations (V003/V005); run `Remove-Item services/*/target -Recurse` + rebuild if stale JARs linger
- **Parity matrix** — `generate-wave20-parity-matrix.mjs` → `BACKEND_FRONTEND_PARITY_MATRIX.json` + `.md`; CI step in `e2e-compose-smoke`
- **Citizen Maestro** — `apps/mobile/maestro/flows/citizen-production-readiness.yaml`
- **Docs** — updated `SOVEREIGN_HOST_PORT_MATRIX.md`, `PRODUCTION_GAPS_AND_NEXT_WAVE.md`

## Wave 20 slice 12 (2026-05-28)

- **Impilo theme on production surfaces** — `impilo-surface-card`, `impilo-subtle-african-accent`, semantic CSS vars on trust banner, workspace shells, command centre, home hero
- **Mobile journey shells** — `ProductionReadinessJourneyScreen` (provider Clinical Tools + citizen My Health); BFF registry fetch with static fallback
- **Maestro** — `apps/mobile/maestro/flows/provider-production-readiness.yaml`
- **Docs** — `SOVEREIGN_HOST_PORT_MATRIX.md`, `PRODUCTION_READINESS_REPORT.md`
- **Dev tooling** — `up.ps1 -ShowHostProfile`; CI uploads `verify-demo-journeys.log` on compose smoke failure

## Wave 20 slice 11 (2026-05-28)

- **Seven journey demo seeds** — BFF Flyway V40 (wellness, telemedicine, BFF admission, `demo_journey_anchors`); inpatient-service V004 (CPID-ZW-00001)
- **`InpatientController`** — web proxy `/internal/v1/inpatient/**` (UI + verify script path)
- **`DemoJourneyController`** — `GET /internal/v1/demo-journeys` (7-row registry)
- **`verify-demo-journeys.mjs`** — +7 journey probes + demo registry check; telemedicine probe uses `patient_id=CPID-ZW-00001`
- **Docs** — `HOW_TO_DEMO_PRODUCTION_READINESS.md`, `HEALTH_OS_PRODUCTION_DEMO_MAP.json`, `PRODUCTION_GAPS_AND_NEXT_WAVE.md`
- **CI** — `e2e-compose-smoke` builds compose, runs verify + production-readiness Playwright (9 tests)
- **Verified on compose :3000** — `verify-demo-journeys.mjs` exit 0 (7/7 journeys; Rx/sovereign probes WARN as expected); Playwright 11/11 with consent seed on citizen-life compose spec
- **Fixes** — removed duplicate inpatient V004 migration; `up.ps1 -Build` uses `up -d --build` (Docker Compose v2+)

## Wave 20 slice 10 (2026-05-28)

- **`/core-transaction` page** — `PlaneWorkspaceShell` + trust banner + BFF transaction feed + related-services cross-links
- **P0 hub polish** — trust banner + Nompilo + related services on `/clinical`, `/pharmacy`, and production command centre
- **Role-aware `/home`** — Production readiness category + worker launch tiles (data-intelligence, inpatient, Rx journey, core-transaction)
- **E2e test IDs** — `trust-context-banner`, `nompilo-context-panel`, `related-services-panel`; sessionStorage auth fixture
- **Playwright** — +4 tests (clinical/pharmacy hubs, core-transaction, role-aware home); 9/9 on dev :3010 (rebuild compose UI with `up.ps1 -Build` for :3000)

## Wave 20 slice 9 (2026-05-28)

- **Rx → pay → dispatch live path** — BFF env for MusheX, dispatch, Nhume, Ndila; web `NhumeController` at `/internal/v1/nhume/**`
- **`nhume.ts`** — canonical BFF base `/internal/v1/nhume` (was `/api/v1/nhume`)
- **`usePharmacy`** — requires `patient_id` for prescription list (matches BFF contract)
- **Rx transaction journey** — live path probe banner + step status from BFF hooks
- **`verify-demo-journeys.mjs`** — +5 Rx-path probes (pharmacy, finance, dispatch, nhume, ndila)
- **E2e** — golden-patient Rx journey live probe test

## Wave 20 slice 8 (2026-05-28)

- **Sovereign services in compose** — `pct-service` (:8088), `integration-hub` (:8110), `sovereign-db-init` (inpatient + pct + impilo_integration_hub DBs)
- **BFF env** — `PCT_BASE_URL`, `INTEGRATION_HUB_BASE_URL` point at in-compose services
- **`verify-demo-journeys.mjs`** — all 5 probes **PASS** (queue + hub routes live)
- **`smoke-bff.mjs`** — PASS (reports idempotency replay WARN when reports-service not on host)
- **Playwright e2e on compose :3000** — 5/5 with `PLAYWRIGHT_SKIP_WEBSERVER=1`, `PLAYWRIGHT_USE_SYSTEM_CHROME=1`
- **Fixes** — compose `$$db` escape; PCT Flyway V007→V009; integration-hub `@EnableJpaRepositories(considerNestedRepositories=true)`; PCT legacy controller profiles; host-port matrix doc

## Wave 20 slice 7 (2026-05-28)

- **inpatient-service in compose** — `:8121`, `inpatient-db-init`, BFF `INPATIENT_BASE_URL=http://inpatient-service:8121`
- **Flyway V004** — demo admission for **CPID-ZW-00001** (Harare Central)
- **`verify-demo-journeys.mjs`** — inpatient admissions **PASS** (1 row via BFF)
- **Compose UI build context** fixed (`context: ../..`); Dockerfile uses `npm install` when lockfile drift blocks `npm ci`
- **BFF startup fixes** — removed duplicate `SocialController` `/composer/assist`; single ctor on `CitizenTelehealthController`
- **`up.ps1` / `up.sh`** — Maven builds `inpatient-service`; documents inpatient health on :8121

## Wave 20 slice 6 (2026-05-28)

- **`verify-demo-journeys.mjs`** + **`smoke-bff.mjs`** (Node smoke for Windows)
- **Playwright e2e** — 5/5 on local dev server
- Live BFF probes (partial upstream)

## Wave 20 slice 5 (2026-05-28)

- Live pipeline metrics, `InpatientController`, Kafka healthcheck fix

## Backend (services)

| Check | Status | Notes |
|-------|--------|-------|
| Experience BFF :8160 | **PASS** | Healthy; proxies PCT + integration-hub in compose |
| pct-service :8088 | **PASS** | In compose; queue probe green |
| integration-hub :8110 | **PASS** | In compose; routes probe green |
| inpatient-service :8121 | **PASS** | In compose; demo admission seeded |
| Sovereign upstreams (host) | **Partial** | Ndila, Nhume, inventory optional on host — see [SOVEREIGN_HOST_PORT_MATRIX.md](./SOVEREIGN_HOST_PORT_MATRIX.md) |
| Sovereign Rx-path overlay | **PASS** (optional) | `up.ps1 -SovereignHost` — pharmacy, Costa billing, MusheX payer-ops, dispatch probes green |

## Docker / compose

| Check | Status | Notes |
|-------|--------|-------|
| `docker compose up` | **PASS** | redis, kafka, db, wellness, inpatient, **pct**, **integration-hub**, BFF, UI |
| one-ui-shell :3000 | **PASS** | Wave 20 routes after Dockerfile rebuild (slice 7+) |

## Remediation before production sign-off

```powershell
cd Impilo-vNext
.\tools\dev\up.ps1              # builds JARs + starts stack including inpatient
.\tools\dev\up.ps1 -Build       # rebuild images when UI lockfile is synced
.\tools\dev\up.ps1 -SovereignHost -Build   # + pharmacy, MusheX, dispatch (green Rx probes)

node scripts/production-readiness/verify-demo-journeys.mjs
node scripts/production-readiness/generate-wave20-parity-matrix.mjs
node scripts/production-readiness/smoke-bff.mjs

cd ui/one-ui-shell
$env:PLAYWRIGHT_USE_SYSTEM_CHROME = "1"
$env:PLAYWRIGHT_SKIP_WEBSERVER = "1"
$env:PLAYWRIGHT_BASE_URL = "http://localhost:3000"
npx playwright test e2e/production-readiness.spec.ts --workers=1
```
