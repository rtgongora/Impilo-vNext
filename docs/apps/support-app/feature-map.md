# Support App Feature Map

This document provides a comprehensive mapping of every feature in the Support App, linking each to its domain, screens, service layer, API route, and backend service.

## Feature Matrix

| Feature Area | Domain | Screen(s) | Service File | API Endpoint | Backend Service | Status |
|---|---|---|---|---|---|---|
| Dashboard Stats | Reporting | DashboardPage | `supportApi.ts` | `GET /internal/v1/support/dashboard/stats` | support-service | IMPLEMENTED |
| Recent Tickets | Reporting | DashboardPage | `supportApi.ts` | `GET /internal/v1/support/tickets?limit=10` | support-service | IMPLEMENTED |
| Ticket List | Tickets | TicketsListPage | `supportApi.ts` | `GET /internal/v1/support/tickets` | support-service | IMPLEMENTED |
| Ticket Filters | Tickets | TicketsListPage | `supportApi.ts` | `GET /internal/v1/support/tickets?status=X&priority=Y&category=Z&assigneeRef=W` | support-service | IMPLEMENTED |
| Ticket Detail | Tickets | TicketDetailPage | `supportApi.ts` | `GET /internal/v1/support/tickets/{id}` | support-service | IMPLEMENTED |
| Create Ticket | Tickets | CreateTicketPage | `supportApi.ts` | `POST /internal/v1/support/tickets` | support-service | IMPLEMENTED |
| Update Status | Tickets | TicketDetailPage | `supportApi.ts` | `PATCH /internal/v1/support/tickets/{id}` | support-service | IMPLEMENTED |
| Assign Ticket | Tickets | TicketDetailPage | `supportApi.ts` | `POST /internal/v1/support/tickets/{id}/assign` | support-service | IMPLEMENTED |
| Assignment History | Tickets | TicketDetailPage | `supportApi.ts` | `GET /internal/v1/support/tickets/{id}/assignments` | support-service | IMPLEMENTED |
| Add Comment | Tickets | TicketDetailPage | `supportApi.ts` | `POST /internal/v1/support/tickets/{id}/comments` | support-service | IMPLEMENTED |
| List Comments | Tickets | TicketDetailPage | `supportApi.ts` | `GET /internal/v1/support/tickets/{id}/comments` | support-service | IMPLEMENTED |
| Escalate Ticket | Incidents | TicketDetailPage | `supportApi.ts` | `POST /internal/v1/support/tickets/{id}/escalate` | support-service | IMPLEMENTED |
| Incident Queue | Incidents | IncidentsPage | `supportApi.ts` | `GET /internal/v1/support/tickets?category=INCIDENT` | support-service | IMPLEMENTED |
| Escalation Queue | Incidents | IncidentsPage | `supportApi.ts` | `GET /internal/v1/support/tickets` (filtered client-side) | support-service | IMPLEMENTED |
| Runbook Links | Incidents | IncidentsPage | Static references | docs/dr/runbooks/* | N/A | IMPLEMENTED |
| Article List | Knowledge | KnowledgePage | `supportApi.ts` | `GET /internal/v1/support/articles` | support-service | IMPLEMENTED |
| Article Search | Knowledge | KnowledgePage | Client-side filter | N/A (local) | N/A | IMPLEMENTED |
| Article Detail | Knowledge | KnowledgePage | `supportApi.ts` | `GET /internal/v1/support/articles/{id}` | support-service | IMPLEMENTED |
| Create Article | Knowledge | KnowledgePage | `supportApi.ts` | `POST /internal/v1/support/articles` | support-service | IMPLEMENTED |
| Publish Article | Knowledge | KnowledgePage | `supportApi.ts` | `PATCH /internal/v1/support/articles/{id}` | support-service | IMPLEMENTED |
| Archive Article | Knowledge | KnowledgePage | `supportApi.ts` | `PATCH /internal/v1/support/articles/{id}` | support-service | IMPLEMENTED |
| Send Message | Messaging | MessagingPage / TicketDetailPage | `supportApi.ts` | `POST /internal/v1/support/tickets/{id}/messages` | support-service | IMPLEMENTED |
| List Messages | Messaging | MessagingPage / TicketDetailPage | `supportApi.ts` | `GET /internal/v1/support/tickets/{id}/messages` | support-service | IMPLEMENTED |
| Diagnostics Linkage | Cross-cutting | TicketDetailPage | `supportApi.ts` | Via correlation_id/request_id on tickets | observability-service | IMPLEMENTED |

## Notes

- All API calls are routed through the trust-aware API client which injects v1.1 trust headers.
- Every write operation publishes a domain event via the transactional outbox pattern.
- The `X-Actor-ID` header identifies the support agent; the `X-Actor-Type` is always `OPERATOR`.
- The "Backend Service" column indicates the primary service handling the domain logic.
- Diagnostics linkage uses correlation IDs stored on tickets to cross-reference with observability dashboards (Grafana, Loki, Tempo).
