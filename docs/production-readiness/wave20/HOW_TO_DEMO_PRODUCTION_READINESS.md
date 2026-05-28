# How to demo production readiness (Wave 20)

> Seven guided journeys for web and mobile. Golden patient: **CPID-ZW-00001** (Tatenda Moyo).

## Prerequisites

```powershell
cd Impilo-vNext
.\tools\dev\up.ps1 -Build
node scripts/production-readiness/verify-demo-journeys.mjs
```

For full Rx path + public-health field tasks:

```powershell
.\tools\dev\up.ps1 -SovereignHost -Build
node scripts/production-readiness/verify-demo-journeys.mjs --sovereign
```

Registry (machine-readable): `GET http://localhost:8160/internal/v1/demo-journeys`

## Journey 1 — Core clinical / Rx

| Step | Surface |
|------|---------|
| 1 | [Production Command Centre](/production-command-centre) — service maturity + hub probe |
| 2 | [Rx transaction journey](/pharmacy/transaction-journey?patientId=CPID-ZW-00001) — live path probes |
| 3 | [Core transactions](/core-transaction) — audit feed |

**BFF probe:** `/internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001`  
**Mobile:** Provider app → Production readiness → Core clinical / Rx

## Journey 2 — Inpatient

| Step | Surface |
|------|---------|
| 1 | [Inpatient hub](/clinical/inpatient) |
| 2 | [Admissions](/clinical/inpatient/admissions) — demo row for CPID-ZW-00001 |
| 3 | [Nursing workbench](/clinical/inpatient/nursing) |

**BFF probe:** `/internal/v1/inpatient/admissions` (≥1 row after Flyway V004)  
**Seed:** `inpatient-service` Flyway `V004__seed_demo_admission.sql`

## Journey 3 — Wellness

| Step | Surface |
|------|---------|
| 1 | [Wellness hub](/wellness) |
| 2 | Citizen [My Life](/home) wellness tiles (compose wellness-service) |

**BFF probe:** `/internal/v1/wellness/challenges` (≥1 row)  
**Seed:** BFF V44 golden-tenant challenge + V26 wellness tables

## Journey 4 — Enterprise resources

| Step | Surface |
|------|---------|
| 1 | [Enterprise hub](/enterprise) |
| 2 | [Inventory](/inventory) requisitions / stock |
| 3 | [Marketplace](/marketplace) services |

**BFF probe:** `/internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001` (≥1 row)  
**Seed:** BFF V31 `inventory_requisitions` (Harare Central)

## Journey 5 — Telemedicine → dispatch

| Step | Surface |
|------|---------|
| 1 | [Telemedicine](/telemedicine) |
| 2 | [Rx journey](/pharmacy/transaction-journey?patientId=CPID-ZW-00001) — dispatch / Nhume steps |
| 3 | [Ndila](/ndila) routing (when sovereign up) |

**BFF probe:** `/internal/v1/mobile/provider/telemedicine/sessions`  
**Seed:** BFF V40 `telemedicine_sessions` for golden patient

## Journey 6 — Public health + geo

| Step | Surface |
|------|---------|
| 1 | [Public health hub](/public-health) |
| 2 | [Public health field tasks](/public-health) (sovereign) or [Ndila](/ndila) (host) |

**BFF probe:** `/internal/v1/public-health/field-tasks` with `--sovereign`, or `/internal/v1/ndila/tiles/config` (SKIP in compose-only)

## Journey 7 — Data & intelligence

| Step | Surface |
|------|---------|
| 1 | [Data & intelligence](/data-intelligence) |
| 2 | [Pipelines](/data-intelligence/pipelines) — hub + transaction metrics |
| 3 | [Audit intel](/data-intelligence/audit) |

**BFF probe:** `/internal/v1/integration-hub/routes` (PASS in compose)

## Playwright (compose)

```powershell
cd ui/one-ui-shell
$env:PLAYWRIGHT_COMPOSE_E2E = "1"
$env:PLAYWRIGHT_SKIP_WEBSERVER = "1"
$env:PLAYWRIGHT_BASE_URL = "http://localhost:3000"
$env:PLAYWRIGHT_USE_SYSTEM_CHROME = "1"
npx playwright test e2e/production-readiness.spec.ts --workers=1
```

## Sign-off checklist

- [ ] `verify-demo-journeys.mjs` — no FAIL rows
- [ ] `verify-demo-journeys.mjs --sovereign` — Rx path + field tasks PASS (with `-SovereignHost`)
- [ ] Inpatient admissions ≥1 row for golden patient
- [ ] Demo journey registry returns 7 journeys
- [ ] Playwright production-readiness 9/9 on compose `:3000`
- [x] Mobile provider `ProductionReadinessJourneyScreen` opens each tab (Clinical Tools → Prod Ready)
- [x] Mobile citizen prod-ready tab lists seven journeys with section navigation

See also: [HEALTH_OS_PRODUCTION_DEMO_MAP.json](./HEALTH_OS_PRODUCTION_DEMO_MAP.json), [PRODUCTION_GAPS_AND_NEXT_WAVE.md](./PRODUCTION_GAPS_AND_NEXT_WAVE.md)
