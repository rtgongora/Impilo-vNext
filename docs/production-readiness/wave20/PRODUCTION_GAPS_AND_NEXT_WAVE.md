# Production gaps and next wave (Wave 20 → Wave 21)

> Honest status after Wave 21 hardening (sovereign overlay + journey probe greens).

## PASS in experience compose slice (default `up.ps1`)

| Area | Evidence |
|------|----------|
| BFF health | `verify-demo-journeys` BFF health |
| Golden patient queue | BFF V4 queue entries |
| Inpatient admissions | `inpatient-service` V004 + `InpatientController` |
| Integration hub routes | In-compose `integration-hub` :8110 |
| PCT queue | In-compose `pct-service` :8088 |
| Demo journey registry | BFF V40 + `GET /internal/v1/demo-journeys` (7 rows) |
| Inventory requisitions | BFF demo fallback (V31-aligned seeds) | Journey 4 |
| Wellness challenges | `wellness-service` + BFF V44 golden-tenant seed |
| Web production-readiness e2e | 9 Playwright tests (compose :3000 after rebuild) |

## SKIP — compose-only (use `-SovereignHost` for greens)

| Probe | Notes |
|-------|-------|
| Ndila tile config | WARN in default compose; PASS with sovereign overlay |
| Nhume deliveries | WARN in default compose; PASS with sovereign overlay |

## PASS with `-SovereignHost` overlay (Slice 13 + Wave 21)

| Probe | Upstream | Notes |
|-------|----------|-------|
| Pharmacy prescriptions | pharmacy-service :8096 | V004 demo dispense order; BFF V41 anchor |
| Costa billing | costing-engine :8101 | V011 demo bill |
| MusheX payment intents | MusheX :8102 | V007/V008 demo ADHOC intent |
| Dispatch deliveries | dispatch-service :8320 | V004 demo dispatch job |
| Public health field tasks | surveillance-service :8180 | V009/V010 table + demo task; BFF proxy |

## Wave 21 (completed 2026-05-28)

1. **Sovereign bring-up** — `wait-sovereign-health.mjs`; wired into `up.ps1` / `up.sh`; CI job `e2e-sovereign-smoke`
2. **Wellness green** — `WellnessDomainController` at `/internal/v1/wellness/challenges`; BFF V44 golden-tenant seed
3. **Inventory green** — `InventoryController.listRequisitions` returns V31-aligned demo rows for Harare Central facility
4. **Surveillance in overlay** — field-tasks API + demo seed
5. **Ndila + Nhume in overlay** — Dockerfiles, compose services, BFF `NhumeController`, nhume V004 golden-tenant seed
6. **Verify script** — `--sovereign` expects Ndila/Nhume PASS

## Flyway repair note

If duplicate-version migrations were applied from stale JARs under `services/*/target/`:

```powershell
Remove-Item services/pharmacy-service/target,services/mushex-service/target,services/dispatch-service/target,services/costing-engine-service/target,services/surveillance-service/target,services/inventory-service/target -Recurse -Force -ErrorAction SilentlyContinue
cd services
mvn -B -pl pharmacy-service,mushex-service,dispatch-service,costing-engine-service,surveillance-service,experience-bff -am -DskipTests package
```

Reset affected DBs when Flyway checksum conflicts appear (see Slice 13 notes in `BUILD_RUN_STATUS.md`).

## Commands

```powershell
cd Impilo-vNext
.\tools\dev\up.ps1 -Build
.\tools\dev\up.ps1 -SovereignHost -Build
node scripts/production-readiness/verify-demo-journeys.mjs
node scripts/production-readiness/verify-demo-journeys.mjs --sovereign
node scripts/production-readiness/wait-sovereign-health.mjs
node scripts/production-readiness/generate-wave20-parity-matrix.mjs
```

See [SOVEREIGN_HOST_PORT_MATRIX.md](./SOVEREIGN_HOST_PORT_MATRIX.md) and [HOW_TO_DEMO_PRODUCTION_READINESS.md](./HOW_TO_DEMO_PRODUCTION_READINESS.md).
