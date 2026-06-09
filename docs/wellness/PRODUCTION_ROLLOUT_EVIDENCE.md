# Simba / Wellness Production Rollout Evidence

Operational rollout evidence pack for **Simba** (`simba-service` — single wellness product runtime).

## 1) Compose / runtime gate

Required local validation:

- `docker compose -f compose/experience/docker-compose.yml up` includes **only** `simba-service:8125` (no `wellness-service` peer)
- BFF proxy: all `/internal/v1/wellness/**` and `/internal/v1/mobile/citizen/**` wellness paths → Simba
- `bash compose/experience/smoke-test.sh` Test 7 — governed clubs via BFF
- `cd services/simba-service && mvn test -Dtest=SimbaWellnessJourneyIT`
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/wellness-journey-flow.spec.ts`
- `PLAYWRIGHT_COMPOSE_E2E=1 npx playwright test e2e/wellness-journey-compose.spec.ts`

## 2) Environment UAT sign-off matrix

Status values: `PENDING`, `IN_PROGRESS`, `SIGNED_OFF`, `BLOCKED`.

| Environment | Tenant | Wellness journey | Screening programmes | Routes / coaching | Sign-off owner | Status |
|---|---|---|---|---|---|---|
| SIT | MOHCC core | `wellness-journey-flow.spec.ts` + compose spec | `/wellness/screenings` Simba panel | `/wellness/routes` + `/wellness/coaching` | Platform ops | SIGNED_OFF |
| SIT | Provincial pilot A | compose smoke Test 7 | HIV annual programme row | Avondale loop route | Provincial lead | IN_PROGRESS |
| UAT | MOHCC core | preview walk `/wellness/*` | guidance reminders + Simba catalogue | device connect governance | MOHCC wellness lead | IN_PROGRESS |
| Pre-prod | MOHCC core |  |  |  |  | PENDING |

### Mandatory journey assertions

- Goal create via `POST /internal/v1/wellness/goals` (Simba).
- Activity log via citizen path or `POST /internal/v1/wellness/activities`.
- Diet entry via `POST /internal/v1/wellness/diet`.
- Club join via `POST /internal/v1/wellness/clubs/{clubId}/join`.
- Challenge join via `POST /internal/v1/wellness/challenges/{challengeId}/join`.
- Screening programmes via `GET /internal/v1/wellness/screening-programmes`.
- Coaching nudges via `GET /internal/v1/wellness/coaching/nudges`.
- Routes catalogue via `GET /internal/v1/wellness/routes`.

## 3) Monitoring and SLO drill evidence

### Target SLOs (initial production baseline)

- **Availability (read APIs):** 99.9% monthly for Simba `/internal/v1/wellness/*` read endpoints.
- **Availability (command APIs):** 99.5% monthly for goals, activities, club/challenge writes.
- **P95 latency:** < 500ms read, < 800ms write under normal load.
- **Outbox publish lag:** p95 < 60s for `simba.wellness.*` events.

### Required failure-mode drills

| Drill | Expected behavior | Evidence artifact |
|---|---|---|
| simba-service pod restart | transient BFF 502 on domain paths; citizen paths unaffected | run log + dashboard |
| simba-service pod restart | All wellness + citizen My Life paths transient 502 | alert timeline |
| BFF mis-route (Simba down) | `502` on `/internal/v1/wellness/goals`; monitoring devices still OK | UI capture + logs |
| kafka unavailable | writes continue; outbox backlog recovers | backlog graph |

### Drill sign-off record

| Date | Environment | Drill | Owner | Result | Notes |
|---|---|---|---|---|---|
| 2026-06-08 | SIT | Split-proxy routing | Platform ops | PASS | Clubs via BFF hit Simba seed |

## 4) Go-live decision gate

Go-live is permitted only when:

- Compose smoke Test 7 is green.
- `SimbaWellnessJourneyIT` passes in CI / VM gates.
- UAT matrix has `SIGNED_OFF` rows for required tenants.
- SLO/failure-mode drills completed with documented evidence.
- No P1/P2 unresolved defects in goals, clubs, challenges, screening programmes, or Health Connect ingest.
