# Backend ↔ Frontend Parity Matrix (Wave 20)

> Generated: 2026-05-28 · `node scripts/production-readiness/generate-wave20-parity-matrix.mjs`

Golden patient: **CPID-ZW-00001**

## Seven demo journeys

| # | Journey | Web | Mobile tab | BFF probe | Maturity |
|---|---------|-----|------------|-----------|----------|
| 1 | Core clinical / Rx | /pharmacy/transaction-journey?patientId=CPID-ZW-00001 | core_transaction | /internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001 | partial |
| 2 | Inpatient | /clinical/inpatient/admissions | inpatient | /internal/v1/inpatient/admissions | partial |
| 3 | Wellness (citizen parity) | /wellness | learning | /internal/v1/wellness/challenges | partial |
| 4 | Enterprise resources | /enterprise | facility | /internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001 | partial |
| 5 | Telemedicine → dispatch | /telemedicine | telemedicine | /internal/v1/mobile/provider/telemedicine/sessions | partial |
| 6 | Public health + geo | /public-health | ph_field_tasks | /internal/v1/ndila/tiles/config | partial |
| 7 | Data & intelligence | /data-intelligence/pipelines | ops_reports | /internal/v1/integration-hub/routes | partial |

## Sovereign domains (Rx-path + enterprise)

| Domain | Plane | Backend | BFF probe | Web | Mobile | Compose | Maturity |
|--------|-------|---------|-----------|-----|--------|---------|----------|
| pharmacy | Clinical / Rx | pharmacy-service | /internal/v1/pharmacy/prescriptions?patient_id=CPID-ZW-00001 | /pharmacy/transaction-journey?patientId=CPID-ZW-00001 | provider:pharmacy / core_transaction | docker-compose.sovereign.yml | partial |
| costa | Finance | costing-engine-service | /internal/v1/finance/billing?page=0&size=1 | /finance/billing | provider:finance / billing | docker-compose.sovereign.yml | partial |
| mushex | Finance | mushex-service | /internal/v1/finance/payer-ops/payment-intents?sourceType=ADHOC&sourceId=CPID-ZW-00001 | /finance/payer-ops | provider:finance / billing | docker-compose.sovereign.yml | partial |
| dispatch | Logistics | dispatch-service | /internal/v1/dispatch/deliveries | /pharmacy/transaction-journey | provider:workflow_dispatch | docker-compose.sovereign.yml | partial |
| nhume | Logistics | nhume-service (host) | /internal/v1/nhume/deliveries | /nhume | citizen:nhume-track | host :8210 | not_wired |
| ndila | Public health + geo | ndila-service (host) | /internal/v1/ndila/tiles/config | /ndila | provider:ph_field_tasks | host :8155 | not_wired |
| inventory | Enterprise | inventory-service (host) | /internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001 | /enterprise | provider:facility | host | partial |
| pct | Clinical | pct-service | /internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001 | /queue | provider:queue | docker-compose.yml | live |
| integration-hub | Data & intelligence | integration-hub | /internal/v1/integration-hub/routes | /data-intelligence/pipelines | provider:ops_reports | docker-compose.yml | live |
| inpatient | Inpatient | inpatient-service | /internal/v1/inpatient/admissions | /clinical/inpatient/admissions | provider:inpatient | docker-compose.yml | partial |
| wellness | Wellness | wellness-service | /internal/v1/wellness/challenges | /wellness | citizen:wellness | docker-compose.yml | partial |

## Compose profiles

| Profile | Command | Rx-path probes |
|---------|---------|----------------|
| Experience (default) | `tools/dev/up.ps1` | WARN on pharmacy/finance/dispatch |
| + Sovereign overlay | `tools/dev/up.ps1 -SovereignHost` | PASS when pharmacy, MusheX, dispatch healthy |

See [SOVEREIGN_HOST_PORT_MATRIX.md](./SOVEREIGN_HOST_PORT_MATRIX.md).
