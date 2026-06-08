# Impilo Fundo Production Rollout Evidence

This document is the operational rollout evidence pack for native Impilo Fundo LMS go-live readiness.

## 1) Helm Render Gate (CI/runtime)

Required CI checks:

- `helm template helm/learning`
- `helm template helm/learning -f helm/learning/values.minimal.yaml`

Both renders must succeed on every PR touching:

- `services/learning-service/**`
- `helm/learning/**`
- `contracts/openapi/learning.openapi.yaml`
- `docs/learning/**`

## 2) Environment UAT Sign-Off Matrix

Status values: `PENDING`, `IN_PROGRESS`, `SIGNED_OFF`, `BLOCKED`.

| Environment | Tenant | Learner journey | Trainer/supervisor reports | Authoring | Assessment moderation | Certificates/CPD evidence | Sign-off owner | Status |
|---|---|---|---|---|---|---|---|---|
| SIT | MOHCC core | `e2e/fundo-learning-flow.spec.ts` + `e2e/fundo-learning-compose.spec.ts` | Reports via `/learning/reports/*` | `/learning/library/uploads` + admin bind | `/learning/admin/moderation` | `verificationDigest` + `/learning/cpd` | Platform ops | SIGNED_OFF |
| SIT | Provincial pilot A | compose smoke Test 5 |  | uploads guide | moderation queue | council bridge e2e | Provincial lead | IN_PROGRESS |
| UAT | MOHCC core | compose + preview walk | cohort reports | studio authoring | manual review UAT | Varapi accept/reject | MOHCC LMS lead | IN_PROGRESS |
| UAT | Provincial pilot A |  |  |  |  |  |  | PENDING |
| Pre-prod | MOHCC core |  |  |  |  |  |  | PENDING |

### Mandatory role-based UAT personas

- Learner (provider)
- Learner (supervisor)
- Trainer/supervisor reviewer
- LMS author/admin
- Operations/on-call engineer

### Mandatory journey assertions

- Catalogue -> course detail -> enrol -> start -> lesson open -> lesson complete.
- Assessment attempt for objective and non-objective questions.
- Manual marking workflow with rubric/feedback persisted.
- Course completion and certificate issuance gating.
- CPD evidence shown as non-authoritative evidence only.

## 3) Monitoring and SLO Drill Evidence

### Target SLOs (initial production baseline)

- **Availability (read APIs):** 99.9% monthly for `/internal/v1/learning/v11/*` read endpoints.
- **Availability (command APIs):** 99.5% monthly for write endpoints.
- **P95 latency:** < 600ms read, < 900ms write under normal load.
- **Outbox publish lag:** p95 < 60s for LMS events.

### Required failure-mode drills

| Drill | Expected behavior | Evidence artifact |
|---|---|---|
| learning-service pod restart under learner traffic | transient retries, no data loss | run log + dashboard screenshot |
| database connection pool saturation | graceful 5xx envelope, alert firing | alert timeline + query stats |
| kafka unavailable (eventing degradation) | core LMS requests continue; outbox backlog rises and recovers | backlog graph + recovery timestamp |
| BFF learning upstream timeout | client-safe error envelopes + UI fallback states | UI capture + logs |
| offline/mobile provider lesson flows | cached reads continue, progress queues for sync | mobile test log + sync reconciliation record |

### Drill sign-off record

| Date | Environment | Drill | Owner | Result | Notes |
|---|---|---|---|---|---|
| 2026-06-08 | SIT | BFF upstream timeout | Platform ops | PASS | Empty catalogue envelope; no fake rows |

## 4) Go-Live Decision Gate

Go-live is permitted only when:

- Helm render gate is green in CI.
- UAT matrix has `SIGNED_OFF` rows for required tenants and personas.
- SLO/failure-mode drills completed with documented evidence.
- No P1/P2 unresolved defects in learner, assessment, certificate, or reporting flows.
