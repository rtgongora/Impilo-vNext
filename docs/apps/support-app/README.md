# Support App

## Overview

The Support App is an operator-facing web application that provides helpdesk and incident management capabilities for support agents, helpdesk teams, incident coordinators, and operational support staff. It delivers five integrated domains — **Tickets**, **Incidents**, **Knowledge**, **Messaging**, and **Reporting** — forming the operational support layer of the Impilo platform.

**Entry point:** `ui/support-console/src/app/layout.tsx`
**Dev server:** `npm -w support-console run dev` (port 3006)

## Architecture

The Support App follows the standard Impilo vNext web application architecture. It is built with Next.js 14.2 (App Router), React 18, Zustand for state management, and TailwindCSS for styling. It consumes the shared-ui workspace package for trust header contracts, API envelope types, and design tokens.

| Dependency | Responsibility |
|---|---|
| `shared-ui` (workspace) | Trust header contracts, ApiEnvelope, PagedResponse, design tokens |
| `@tanstack/react-query` | Async server state management |
| `zustand` | Client session and work context state |
| `tailwindcss` | Styling with Impilo design system tokens |

## Trust Model

Every HTTP request carries v1.1 trust headers via the trust-aware API client (`src/lib/apiClient.ts`). Authentication is handled via Keycloak with bearer tokens. The support agent identity is resolved from the `X-Actor-ID` header with actor type `OPERATOR`.

The API client injects:
- `x-tenant-id` — from work context
- `x-actor-id` — from session
- `x-actor-type` — always `OPERATOR`
- `x-purpose-of-use` — always `OPERATIONS`
- `x-correlation-id` — auto-generated per request
- `x-facility-id` — optional, from work context
- `Idempotency-Key` — auto-generated for all mutations (POST/PATCH/PUT)

## Roles

| Role | Scope |
|---|---|
| `SUPPORT_AGENT` | Ticket CRUD, comments, messaging, knowledge base read |
| `SUPPORT_LEAD` | Agent capabilities + ticket assignment, escalation management |
| `INCIDENT_COORDINATOR` | Lead capabilities + incident queue management, runbook access |
| `HELPDESK_ADMIN` | Full access including knowledge base authoring, dashboard analytics, system configuration |

## Domains

### Dashboard

Operational overview with real-time metrics:
- Ticket counts by status (open, in-progress, resolved, closed)
- Priority distribution (critical, high, medium, low)
- Escalation count and average resolution time
- Quick-action links to ticket queue, incidents, and knowledge base

### Tickets

Complete ticket lifecycle management:
- **List/Search/Filter** — filter by status, priority, category, assignee with pagination
- **Detail View** — full ticket information with comments, messages, assignments, diagnostics linkage
- **Create** — new ticket creation with category, priority, reporter, and facility assignment
- **Update Status** — state machine transitions (OPEN → IN_PROGRESS → WAITING → RESOLVED → CLOSED)
- **Assign/Reassign** — assign tickets to agents with full assignment history
- **Comments** — internal comment threads for agent collaboration
- **Diagnostics** — correlation ID and request ID linkage to observability dashboards

### Incidents

Incident and escalation management:
- **Incident Queue** — tickets categorized as INCIDENT, filtered for active incidents
- **Escalation Queue** — all tickets with escalation level > 0
- **Summary Cards** — active incidents, critical count, L2+ escalations, resolved today
- **Runbook Links** — direct references to DR runbooks (ring-0 failover, DB restore, Kafka recovery, partial recovery)
- **Service Context** — facility and correlation ID linkage per incident

### Knowledge

Knowledge base for support staff:
- **Article List/Search** — browse and filter articles by status and category
- **Article Detail** — full article view with metadata, tags, and author
- **Create Article** — author new articles with category and tag assignment
- **Publish/Archive** — lifecycle management (DRAFT → PUBLISHED → ARCHIVED)
- **Ticket-Context Suggestions** — articles surfaced alongside ticket categories

### Messaging

Ticket-linked conversation threads:
- **Active Ticket List** — conversations linked to open/in-progress tickets
- **Message Thread** — real-time-style conversation view with sender type indicators
- **Send Messages** — agent-side messaging with AGENT sender type
- **Requester History** — full message thread per ticket

### Reporting

Dashboard-level analytics:
- Ticket volume by status and priority
- Escalation metrics
- Average resolution time
- SLA-style aging views

## Backend Integration

All API calls go directly to the support-service at `/internal/v1/support/*`. The trust-aware API client handles header injection and error envelope parsing.

| Domain | API Endpoint | Backend Service |
|---|---|---|
| Tickets | `GET/POST/PATCH /internal/v1/support/tickets` | support-service |
| Comments | `GET/POST /internal/v1/support/tickets/{id}/comments` | support-service |
| Assignments | `GET/POST /internal/v1/support/tickets/{id}/assign` | support-service |
| Escalation | `POST /internal/v1/support/tickets/{id}/escalate` | support-service |
| Messages | `GET/POST /internal/v1/support/tickets/{id}/messages` | support-service |
| Articles | `GET/POST/PATCH /internal/v1/support/articles` | support-service |
| Dashboard | `GET /internal/v1/support/dashboard/stats` | support-service |
| Snapshots | `GET /internal/v1/snapshots/tickets` | support-service |

## Database Schema

Support-specific tables are created in Flyway migrations V001–V004:

- `sup_tickets` — ticket records with status, priority, category, escalation fields
- `sup_ticket_comments` — comment threads per ticket
- `sup_ticket_assignments` — assignment history per ticket
- `sup_support_messages` — conversation messages per ticket
- `sup_knowledge_articles` — knowledge base articles
- `sup_event_outbox` — transactional outbox for Kafka event publishing
- `sup_idempotency_keys` — idempotency deduplication

All tables enforce tenant isolation via `tenant_id` column.

## Event Topics

All mutations emit events via the transactional outbox pattern on the `impilo.support.*` namespace:

| Event | Trigger |
|---|---|
| `impilo.support.ticket.created.v1` | Ticket creation |
| `impilo.support.ticket.updated.v1` | Ticket field update |
| `impilo.support.ticket.escalated.v1` | Ticket escalation |
| `impilo.support.ticket.assigned.v1` | Ticket assignment |
| `impilo.support.ticket.comment.added.v1` | Comment added |
| `impilo.support.message.sent.v1` | Support message sent |
| `impilo.support.article.created.v1` | Article creation |
| `impilo.support.article.updated.v1` | Article update |
