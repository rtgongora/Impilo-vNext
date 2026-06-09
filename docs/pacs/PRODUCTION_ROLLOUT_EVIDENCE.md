# PACS / Imaging Production Rollout Evidence

Operational rollout evidence for governed imaging metadata + viewer path.

## 1) Compose / runtime gate

Experience compose includes:

- `orthanc:8042`
- `pacs-adapter-service:8113` with `impilo_pacs` database
- BFF `PACS_BASE_URL` + `ORTHANC_BASE_URL` pointed at compose services

Validation:

- `bash compose/experience/smoke-test.sh` Test 11 (imaging studies)
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/imaging-order-viewer-compose.spec.ts`

## 2) Mandatory assertions

- Governed study list: `GET /internal/v1/imaging/studies?patient_cpid=CPID-ZW-00001` returns seed **Chest X-ray**
- Mobile provider imaging: `GET /internal/v1/mobile/provider/imaging/studies?patient_cpid=...` proxies pacs-adapter
- SHR linkage intent: `POST /internal/v1/clinical/shr-artifacts` returns **202 ACCEPTED** with `linkage_id` (pending Butano write)

## 3) Go-live gate

- Smoke Test 11 green
- Orthanc + pacs-adapter healthy in target namespace
- Registry `production_status: pilot-ready-enrolled-pacs`
