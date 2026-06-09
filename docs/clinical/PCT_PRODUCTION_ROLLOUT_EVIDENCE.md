# PCT Production Rollout Evidence

Operational rollout evidence pack for pct-service queue / encounter spine.

## 1) Compose / runtime gate

- `docker compose -f compose/experience/docker-compose.yml up` includes `pct-service:8088`
- `bash compose/experience/smoke-test.sh` Test 8 — governed queue via BFF
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/pct-queue-compose.spec.ts`

## 2) Mandatory journey assertions

- Queue list: `GET /internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001`
- Seed queue **Harare Central OPD** with patient **CPID-ZW-00001**
- Backend IT: `PctQueueEncounterIT` (journey → enqueue → call-next → encounter)

## 3) UAT sign-off matrix

| Environment | Queue journey | Encounter start | Sign-off owner | Status |
|---|---|---|---|---|
| SIT | smoke Test 8 + `pct-queue-compose.spec.ts` | `PctQueueEncounterIT` | Platform ops | SIGNED_OFF |
| UAT | preview walk `/queue` | call-next manual | Clinical lead | IN_PROGRESS |
| Pre-prod | — | — | — | PENDING |

## 4) Go-live gate

- Compose smoke Test 8 green
- No P1 defects on `/queue` or encounter orchestration rails
- Registry `production_status: pilot-ready-enrolled-pct`
