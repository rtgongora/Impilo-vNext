# Coverage Production Rollout Evidence

Operational rollout evidence pack for coverage-service and citizen enrollment go-live readiness.

## 1) Compose / runtime gate

Required local validation:

- `docker compose -f compose/experience/docker-compose.yml up` includes `coverage-service:8140`
- `bash compose/experience/smoke-test.sh` Test 6 — governed plans via BFF
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/coverage-enroll-compose.spec.ts`

## 2) Environment UAT sign-off matrix

Status values: `PENDING`, `IN_PROGRESS`, `SIGNED_OFF`, `BLOCKED`.

| Environment | Tenant | Enrollment journey | Member dashboard | Subsidies | Intelligence KPIs | Sign-off owner | Status |
|---|---|---|---|---|---|---|---|
| SIT | MOHCC core | `e2e/coverage-enroll-flow.spec.ts` + `e2e/coverage-enroll-compose.spec.ts` | `/coverage/member` | `/coverage` Subsidies tab | utilization + remittance rows | Platform ops | SIGNED_OFF |
| SIT | Provincial pilot A | compose smoke Test 6 | member self-service | subsidy programmes | intelligence tab | Provincial lead | IN_PROGRESS |
| UAT | MOHCC core | compose + preview walk | appeals/preauth UAT | subsidy allocation rules | finance remittance hub | MOHCC finance lead | IN_PROGRESS |
| Pre-prod | MOHCC core |  |  |  |  |  | PENDING |

### Mandatory journey assertions

- Plans load from `GET /internal/v1/coverage/plans`.
- Pre-enrollment eligibility via `POST /internal/v1/coverage/eligibility/enrollment`.
- Member create via `POST /internal/v1/coverage/members`.
- Member dashboard shows active plan and member number.
- Finance remittance hub at `/finance/remittances` reads the same canonical feed.

## 3) Monitoring and SLO drill evidence

### Target SLOs (initial production baseline)

- **Availability (read APIs):** 99.9% monthly for `/internal/v1/coverage/*` read endpoints.
- **Availability (command APIs):** 99.5% monthly for enrollment and claim writes.
- **P95 latency:** < 500ms read, < 800ms write under normal load.
- **Outbox publish lag:** p95 < 60s for coverage events.

### Required failure-mode drills

| Drill | Expected behavior | Evidence artifact |
|---|---|---|
| coverage-service pod restart | transient BFF 502, recovery without data loss | run log + dashboard screenshot |
| database connection saturation | graceful 5xx envelope, alert firing | alert timeline |
| kafka unavailable | requests continue; outbox backlog recovers | backlog graph |
| BFF coverage upstream timeout | `COVERAGE_UNAVAILABLE` envelope + UI error state | UI capture + logs |

### Drill sign-off record

| Date | Environment | Drill | Owner | Result | Notes |
|---|---|---|---|---|---|
| 2026-06-08 | SIT | BFF upstream timeout | Platform ops | PASS | Plans envelope empty; no stub rows |

## 4) Go-live decision gate

Go-live is permitted only when:

- Compose smoke Test 6 is green.
- UAT matrix has `SIGNED_OFF` rows for required tenants.
- SLO/failure-mode drills completed with documented evidence.
- No P1/P2 unresolved defects in enrollment, claims, remittance, or subsidy surfaces.
