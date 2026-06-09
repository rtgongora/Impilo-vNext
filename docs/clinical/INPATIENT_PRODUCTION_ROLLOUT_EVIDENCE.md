# Inpatient Production Rollout Evidence

Operational rollout evidence for inpatient-service admissions, beds, and ward rounds.

## 1) Compose / runtime gate

- `inpatient-service:8121` in experience compose
- `bash compose/experience/smoke-test.sh` Tests 9 (admissions + ward rounds)
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/inpatient-admission-compose.spec.ts`

## 2) Admission doctrine

See [ADR-INPATIENT-PCT-ADMISSION-ORCHESTRATION.md](../adr/ADR-INPATIENT-PCT-ADMISSION-ORCHESTRATION.md):

- **PCT** owns journey-linked admission workflow (outpatient → inpatient handoff)
- **inpatient-service** owns canonical inpatient records (bed, ward, rounds, discharge)

## 3) Mandatory assertions

- Admissions list returns demo seed `CPID-ZW-00001` / `f2000000-0000-0000-0000-000000000001`
- Ward rounds: `GET /internal/v1/inpatient/admissions/{admissionRef}/ward-rounds` returns seed round led by Dr. Tendai Mapfumo
- BFF ward-round POST paths proxy to inpatient-service (no 501)

## 4) Go-live gate

- Smoke Tests 9 green
- Ward rounds sovereign API implemented
- Registry `production_status: pilot-ready-enrolled-inpatient`
