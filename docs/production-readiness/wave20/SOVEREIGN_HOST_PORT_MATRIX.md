# Sovereign host port matrix (Wave 20 Slice 13)

Experience compose runs **in-network** sovereigns (PCT, integration-hub, inpatient, wellness). The **sovereign overlay** brings pharmacy, Costa, MusheX, dispatch, surveillance, Ndila, and Nhume into compose.

| Service | Host port | BFF env (compose) | Used by journey / probe |
|---------|-----------|-------------------|-------------------------|
| experience-bff | 8160 | (in compose) | All probes |
| one-ui-shell | 3000 | (in compose) | Web demo |
| pct-service | 8088 | `PCT_BASE_URL` | 1 Queue, 5 Telemedicine |
| integration-hub | 8110 | `INTEGRATION_HUB_BASE_URL` | 7 Data & intelligence |
| inpatient-service | 8121 | `INPATIENT_BASE_URL` | 2 Inpatient |
| wellness-service | 8161 | `WELLNESS_SERVICE_BASE_URL` | 3 Wellness |
| pharmacy-service | 8096 | `PHARMACY_BASE_URL` | 1 Rx prescriptions |
| costing-engine (Costa) | 8101 | `COSTA_BASE_URL` | 1 Rx billing list |
| MusheX / payment intents | 8102 | `MUSHEX_BASE_URL` | Rx payer-ops |
| dispatch | 8320 | `DISPATCH_BASE_URL` | 5 Dispatch |
| nhume | 8210 | `NHUME_BASE_URL` | 5 Nhume deliveries (sovereign overlay) |
| Ndila | 8155 | `NDILA_BASE_URL` | 6 Public health + geo (sovereign overlay) |
| inventory | (host) | BFF `inventory_requisitions` (V31) | 4 Enterprise |
| surveillance | 8180 | `SURVEILLANCE_BASE_URL` | 6 Field tasks (sovereign overlay) |
| Vito (PHID) | 8082 | `VITO_BASE_URL` | Registry / identity |
| Varapi | 8083 | `VARAPI_BASE_URL` | Registry verification |

## Compose profiles

| Profile | Command | Rx-path probes |
|---------|---------|----------------|
| Experience (default) | `.\tools\dev\up.ps1` | WARN on pharmacy/finance/dispatch |
| + Sovereign overlay | `.\tools\dev\up.ps1 -SovereignHost [-Build]` | PASS pharmacy, Costa, MusheX, dispatch, surveillance, Ndila, Nhume |

The overlay uses `compose/experience/docker-compose.sovereign.yml` — creates sovereign DBs and starts Rx-path + geo/logistics services in-network.

Demo seeds: pharmacy V004, MusheX V007/V008, Costa V011, dispatch V004, surveillance V009/V010, nhume V004, BFF V41–V44.

## Optional host profile (Windows)

Start experience compose first:

```powershell
cd Impilo-vNext
.\tools\dev\up.ps1 -Build
```

For **green Rx-path probes without host services**:

```powershell
.\tools\dev\up.ps1 -SovereignHost -Build
node scripts/production-readiness/wait-sovereign-health.mjs
node scripts/production-readiness/verify-demo-journeys.mjs --sovereign
```

Ndila and Nhume are included in the sovereign overlay (no separate host start required).

## Docker Desktop note

BFF in compose reaches host services via `host.docker.internal`. On Linux CI, host sovereigns are typically not started — WARN is acceptable for Ndila/Nhume; base compose smoke does not use `-SovereignHost`.
