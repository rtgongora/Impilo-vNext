# NCZ register materialiser + regulatory UI completeness

**Landed** 2026-07-31 · **Branch** `claude/staging-ux-orchestration-remediation-Yypyl` · **Lane** NCZ council-pack / ROM

## What landed

### Task #97 — register materialiser

- Varapi `V043` provenance (`CONFIG_PACK` / `MIGRATION_SEED`), retirement, delete guard.
- Idempotent reconcile from activated org-registry configuration (Kafka listener + operator POST).
- Operator browse: org-scoped registers with provenance; shell `/work/regulatory/[orgId]/registers`.

### Regulatory UI completeness (no stub consoles)

| Surface | Path / API | Notes |
|---|---|---|
| Student applications queue | `/work/regulatory/[orgId]/student-applications` | Varapi list + BFF proxy |
| Student review + **admit** | `…/student-applications/[applicationId]` | Fee gate before admit; returns index + entry id |
| W1D reports | `…/student-reports` | Regulator board, ageing, returns, institution board |
| CPD review | `…/cpd-review` | Provider-scoped Fundo candidates; no fake council-wide queue |
| Restrictions | `…/restrictions` | Read-only; impose via disciplinary |
| Audit | `…/audit` | Org-scoped **configuration** audit (honest residual gap stated) |
| Public registers | `PublicRegulatoryExplorer` | Gateway `…/councils/{code}/registers` |
| Applicant resubmit | `/professional/regulatory/apply/student/…` | Non-empty section content required |
| Stub redirects | `/work/regulators/…/{cpd,restrictions,audit,cases,licence-review}` | No `ScopedAdministrationSurface` |

`EXPECTED_ROUTE_COUNT` after tip rebase: **839** (shared tip 833 + registers +5 completeness routes).

### Mobile person journeys (not operator desks)

- Provider Professional → **My Regulatory Affairs** (`/internal/v1/me/regulatory/*`, practice, student, contributor invite).
- Deep links: `/professional/regulatory`, `/professional/regulatory/contribute/{inviteId}`.
- Citizen Personal → Councils (public explore).
- Operator reconcile / student **review** / W1D boards / config write remain **WEB_ONLY**.

## Verification (pre-push)

- `npx vitest run` routes + student/registers/desk golden threads — pass.
- `mvn -o -pl varapi-service -Dtest=StudentApplicationListTest,RegisterEntryRestrictionControllerTest,StudentApplicationControllerRoutingTest test` — pass.
- `mvn -o -pl experience-bff,organization-registry-service -DskipTests compile` — pass.

## Still open (not this land)

- First-flight of org-registry **Kafka outbox** publish path — see [`2026-07-30-org-registry-event-path-first-flight.md`](2026-07-30-org-registry-event-path-first-flight.md). Manual reconcile remains the recovery path.
- Preview deploy of varapi / experience-bff / org-registry — requires PO authorize after local quality gates.

## Non-goals preserved

- Configuration write studio (four-eyes; read-only by design).
- Inventing a council-wide CPD queue.
- Imposing restrictions outside disciplinary determine.
