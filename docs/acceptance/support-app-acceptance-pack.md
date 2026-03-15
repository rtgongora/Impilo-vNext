# Support App — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Wave: M3 (Support)

---

## 1. Scope

This acceptance pack covers the Support App vertical slice, including:
- Web frontend: `ui/support-console/`
- Backend service: `services/support-service/`
- Documentation: `docs/apps/support-app/`

---

## 2. Merge Gate Compliance

### Rule 1: No App May Be Merged Without Its Backend Dependencies

| Feature | Backend Endpoint | Service | Evidence |
|---|---|---|---|
| Dashboard Stats | `GET /internal/v1/support/dashboard/stats` | support-service | DashboardController.java |
| Ticket CRUD | `GET/POST/PATCH /internal/v1/support/tickets` | support-service | TicketController.java |
| Ticket Comments | `GET/POST /internal/v1/support/tickets/{id}/comments` | support-service | CommentController.java |
| Ticket Assignment | `GET/POST /internal/v1/support/tickets/{id}/assign` | support-service | AssignmentController.java |
| Ticket Escalation | `POST /internal/v1/support/tickets/{id}/escalate` | support-service | TicketController.java |
| Ticket Messages | `GET/POST /internal/v1/support/tickets/{id}/messages` | support-service | MessageController.java |
| Knowledge Articles | `GET/POST/PATCH /internal/v1/support/articles` | support-service | ArticleController.java |
| Snapshots | `GET /internal/v1/snapshots/tickets` | support-service | SnapshotController.java |

**Status: PASS** — All frontend features call real backend endpoints implemented in support-service.

### Rule 2: No App May Be Merged Without Documentation Updates

| Document | Path | Status |
|---|---|---|
| Support App README | `docs/apps/support-app/README.md` | CREATED |
| Feature Map | `docs/apps/support-app/feature-map.md` | CREATED |
| Ops Workflows | `docs/apps/support-app/ops-workflows.md` | CREATED |
| Acceptance Pack | `docs/acceptance/support-app-acceptance-pack.md` | CREATED |
| Compliance Matrix | `docs/compliance/full-platform-compliance-matrix.md` | UPDATED |

**Status: PASS** — All required documentation created and updated.

### Rule 3: No App May Be Merged Without Acceptance Pack Updates

See Golden Path Tests (Section 4) below.

**Status: PASS** — All golden paths documented and verified.

### Rule 4: No App May Be Merged Without Shared Foundation Reuse

| Shared Package | Usage |
|---|---|
| `shared-ui` (workspace) | Trust headers (TRUST_HEADERS), ApiEnvelope, PagedResponse, design tokens |
| Design tokens | TailwindCSS config extends shared brand colors, typography, spacing |
| API client pattern | Follows ops-console apiClient.ts pattern with trust header injection |
| Session store pattern | Follows ops-console sessionStore.ts pattern with Zustand |

**Status: PASS** — App reuses shared-ui contracts and established patterns.

---

## 3. Vertical Slice Verification

| Layer | Requirement | Evidence | Status |
|---|---|---|---|
| **Web UI** | Screens implemented with real navigation, data binding, error states, loading states | All 7 pages implemented with error/loading states | PASS |
| **Shared Foundation** | Uses shared-ui contracts and patterns | apiClient.ts imports TRUST_HEADERS from shared-ui | PASS |
| **Backend Service** | All endpoints exist and pass golden contract test | SupportGoldenContractIT + SupportApiMockMvcTest | PASS |
| **Database** | Migrations exist, schema matches domain model | V001–V004 Flyway migrations | PASS |
| **Outbox** | event_outbox row emitted for every mutation | 8 event types on impilo.support.* namespace | PASS |
| **v1.1 Compliance** | 4-header enforcement, idempotency, error envelope | CompanionHeaders used in all controllers | PASS |
| **Tests** | Unit + integration + golden path | SupportApiMockMvcTest, SupportExtendedApiTest, SupportGoldenContractIT, frontend integration tests | PASS |

---

## 4. Golden Path Tests

### GP-1: Ticket Creation and Retrieval

1. Support agent creates ticket via POST /internal/v1/support/tickets
2. Response returns 201 with ticketId, status=OPEN
3. GET /internal/v1/support/tickets/{id} returns full ticket
4. Outbox contains `impilo.support.ticket.created.v1` event
5. **Expected:** Ticket persisted with all fields, outbox event emitted
6. **Verification:** SupportApiMockMvcTest.TicketCrud.createTicket
7. **Status: PASS**

### GP-2: Ticket Status Update to Resolved

1. Create ticket (setup)
2. PATCH /internal/v1/support/tickets/{id} with status=RESOLVED, resolution="Fixed"
3. Response shows status=RESOLVED, resolvedAt set
4. Outbox contains `impilo.support.ticket.updated.v1` event
5. **Expected:** Status updated, resolvedAt timestamp set
6. **Verification:** SupportApiMockMvcTest.TicketCrud.updateTicketToResolved
7. **Status: PASS**

### GP-3: Ticket Assignment

1. Create ticket (setup)
2. POST /internal/v1/support/tickets/{id}/assign with assigneeRef and assignedBy
3. Response returns assignment record
4. GET ticket shows updated assigneeRef
5. GET assignments shows history
6. Outbox contains `impilo.support.ticket.assigned.v1` event
7. **Expected:** Ticket reassigned, history recorded
8. **Verification:** SupportExtendedApiTest
9. **Status: PASS**

### GP-4: Ticket Escalation

1. Create ticket (setup)
2. POST /internal/v1/support/tickets/{id}/escalate with targetLevel=1
3. Response shows escalationLevel=1, escalatedAt set
4. Repeat escalation to level 3
5. Ticket priority auto-promoted to CRITICAL
6. Outbox contains `impilo.support.ticket.escalated.v1` events
7. **Expected:** Escalation tracked, priority promoted at L3
8. **Verification:** SupportExtendedApiTest
9. **Status: PASS**

### GP-5: Comment Thread

1. Create ticket (setup)
2. POST /internal/v1/support/tickets/{id}/comments with authorRef and body
3. Response returns 201 with comment
4. GET comments returns thread
5. Outbox contains `impilo.support.ticket.comment.added.v1`
6. **Expected:** Comment persisted, outbox event emitted
7. **Verification:** SupportExtendedApiTest
8. **Status: PASS**

### GP-6: Messaging Thread

1. Create ticket (setup)
2. POST /internal/v1/support/tickets/{id}/messages with senderRef, senderType=AGENT, body
3. Response returns 201 with message
4. GET messages returns conversation
5. Outbox contains `impilo.support.message.sent.v1`
6. **Expected:** Message persisted in thread, outbox event emitted
7. **Verification:** SupportExtendedApiTest
8. **Status: PASS**

### GP-7: Knowledge Article Lifecycle

1. POST /internal/v1/support/articles with title, body, authorRef
2. Response returns 201 with articleId, status=DRAFT
3. PATCH /internal/v1/support/articles/{id} with status=PUBLISHED
4. Article status updated, publishedAt set
5. Outbox contains `impilo.support.article.created.v1` and `impilo.support.article.updated.v1`
6. **Expected:** Article lifecycle DRAFT → PUBLISHED works
7. **Verification:** SupportExtendedApiTest
8. **Status: PASS**

### GP-8: Dashboard Stats

1. Create several tickets with different statuses and priorities
2. GET /internal/v1/support/dashboard/stats
3. Response returns counts matching created tickets
4. **Expected:** Stats reflect actual database state
5. **Verification:** SupportExtendedApiTest
6. **Status: PASS**

### GP-9: Diagnostics Linkage

1. Create ticket with X-Correlation-ID and X-Request-ID headers
2. GET ticket shows correlationId and requestId in response
3. **Expected:** Trace IDs preserved for observability cross-reference
4. **Verification:** SupportApiMockMvcTest.RequestCorrelationTracking
5. **Status: PASS**

### GP-10: Missing Headers Enforcement

1. POST /internal/v1/support/tickets without X-Tenant-ID
2. Response returns 400 with MISSING_REQUIRED_HEADER error envelope
3. **Expected:** v1.1 header enforcement active
4. **Verification:** SupportApiMockMvcTest.MissingHeaders
5. **Status: PASS**

---

## 5. Prohibited Pattern Audit

| Pattern | Found? | Evidence |
|---|---|---|
| Mock API responses | NO | All API calls in supportApi.ts use real apiClient |
| TODO/FIXME/STUB/MOCK | NO | Codebase scan clean |
| Stub services | NO | All controllers backed by SupportService with DB |
| Feature flags hiding incomplete features | NO | All features fully implemented |
| Direct service calls bypassing trust headers | NO | apiClient.ts injects all trust headers |
| Custom auth logic | NO | Uses shared-ui contracts and Keycloak bearer tokens |

**Status: PASS** — No prohibited patterns found.

---

## 6. Compliance Summary

| Requirement | Status |
|---|---|
| Trust Headers v1.1 | PASS — 14 headers per contracts.ts, injected by apiClient |
| Idempotency | PASS — Idempotency-Key on all mutations |
| Error Envelope | PASS — ApiEnvelope with success/data/error/correlationId/timestamp |
| Event Outbox | PASS — 8 event types, v1.1 columns |
| EventEnvelope | PASS — outbox events include causation chain |
| Tenant Isolation | PASS — all queries filtered by tenantId |
| Consistency | PASS — STRONG consistency for ticket writes, EVENTUAL for event propagation |

---

## 7. Sign-Off

| Role | Name | Date | Status |
|---|---|---|---|
| Wave Lead | (pending) | | |
| Engineering | (auto-verified) | 2026-03-15 | PASS |
| Documentation | (auto-verified) | 2026-03-15 | PASS |
| Compliance | (pending) | | |
