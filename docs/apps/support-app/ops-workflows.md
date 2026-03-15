# Support App — Operational Workflows

This document describes the key operational workflows implemented in the Support App, including ticket lifecycle, escalation procedures, incident management, and knowledge base operations.

---

## 1. Ticket Lifecycle

### States
```
OPEN → IN_PROGRESS → WAITING → RESOLVED → CLOSED
                  ↘           ↗
                   → CLOSED →
                   ← OPEN ←
```

### State Transitions

| From | To | Trigger | Side Effects |
|---|---|---|---|
| OPEN | IN_PROGRESS | Agent picks up ticket | Assignment created |
| OPEN | WAITING | Awaiting external input | - |
| OPEN | CLOSED | Duplicate/invalid | - |
| IN_PROGRESS | WAITING | Awaiting requester response | - |
| IN_PROGRESS | RESOLVED | Fix applied | `resolvedAt` set, resolution recorded |
| IN_PROGRESS | CLOSED | Cancelled | - |
| WAITING | IN_PROGRESS | Requester responds | - |
| WAITING | RESOLVED | Issue self-resolved | `resolvedAt` set |
| WAITING | CLOSED | No response timeout | - |
| RESOLVED | CLOSED | Confirmed resolved | - |
| RESOLVED | OPEN | Regression/reopen | `resolvedAt` cleared |
| CLOSED | OPEN | Reopen (rare) | - |

### Event Trail

Every state transition emits `impilo.support.ticket.updated.v1` to the outbox, carrying the full ticket state as payload. This enables downstream consumers (audit, analytics, notification) to react to status changes.

---

## 2. Escalation Workflow

### Escalation Levels

| Level | Description | Priority Impact | Notification |
|---|---|---|---|
| L0 | Normal handling | No change | Standard queue |
| L1 | First escalation | No change | Support lead notified |
| L2 | Second escalation | No change | Incident coordinator notified |
| L3+ | Critical escalation | Priority auto-set to CRITICAL | On-call engineering notified |

### Escalation Process

1. Agent or lead triggers escalation from ticket detail page
2. System increments `escalation_level`, records `escalated_by` and `escalated_at`
3. If level >= 3, ticket priority is automatically promoted to `CRITICAL`
4. Outbox event `impilo.support.ticket.escalated.v1` is emitted
5. Ticket appears in the escalation queue on the Incidents page

### SLA Breach

When a ticket's SLA is breached (detected by external monitoring or timer), the `sla_breached_at` timestamp is set. This is displayed prominently on the ticket detail page and in the incidents queue.

---

## 3. Incident Management

### Incident Identification

Tickets are classified as incidents through:
- **Category**: Ticket created with `category = INCIDENT`
- **Escalation**: Any ticket escalated to L2+ is treated as an operational incident

### Incident Queue

The Incidents page provides:
1. **Active Incidents** — all INCIDENT-category tickets that are OPEN or IN_PROGRESS
2. **Escalation Queue** — all tickets (any category) with escalation_level > 0
3. **Summary Metrics** — active count, critical count, L2+ escalations, resolved today

### Runbook Integration

The Incidents page links to disaster recovery runbooks:

| Runbook | Path | Use Case |
|---|---|---|
| Ring-0 Failover | `docs/dr/runbooks/ring0-failover.md` | Complete service failover |
| DB Restore | `docs/dr/runbooks/db-restore.md` | Database recovery procedures |
| Kafka Recovery | `docs/dr/runbooks/kafka-recovery.md` | Message broker recovery |
| Partial Recovery | `docs/dr/runbooks/partial-platform-recovery.md` | Service-level recovery |

### Diagnostics Linkage

Every ticket can store:
- `correlation_id` — links to distributed traces in Tempo
- `request_id` — links to specific request logs in Loki

Support agents can use these identifiers to search observability dashboards (Grafana, Loki, Tempo) for related logs, traces, and metrics.

---

## 4. Assignment Workflow

### Process

1. Support lead or agent initiates assignment from ticket detail sidebar
2. System creates an `AssignmentEntity` record with `assignee_ref`, `assigned_by`, and timestamp
3. Ticket's `assignee_ref` is updated to the new assignee
4. If the ticket was OPEN, it transitions to IN_PROGRESS
5. Outbox event `impilo.support.ticket.assigned.v1` is emitted
6. Full assignment history is visible on the ticket detail page

### Reassignment

Reassignment follows the same process. The previous assignment remains in history (no `unassigned_at` is set on automatic reassignment). This provides a complete audit trail of ticket ownership.

---

## 5. Messaging Workflow

### Conversation Model

Each ticket has an associated message thread. Messages are typed by sender:

| Sender Type | Description |
|---|---|
| `AGENT` | Support agent response |
| `REQUESTER` | End-user/citizen message (via citizen app) |
| `SYSTEM` | Automated system message (status change, escalation) |

### Process

1. Agent selects active ticket from messaging inbox
2. Full message history loads for the selected ticket
3. Agent types and sends message (recorded as `AGENT` sender type)
4. Outbox event `impilo.support.message.sent.v1` is emitted
5. Downstream consumers can trigger push notifications to the requester

### Contact History

The messaging page shows all active (non-closed, non-resolved) tickets, allowing agents to quickly switch between conversations.

---

## 6. Knowledge Base Workflow

### Article Lifecycle

```
DRAFT → PUBLISHED → ARCHIVED
```

### Process

1. **Author** — Support lead or admin creates article with title, body, category, and tags
2. **Review** — Article remains in DRAFT status for review
3. **Publish** — Admin publishes article, making it available to all agents
4. **Archive** — Outdated articles are archived (not deleted)

### Ticket-Context Suggestions

When viewing a ticket, agents can navigate to the knowledge base and search for articles matching the ticket's category. This helps agents find relevant troubleshooting guides and standard operating procedures.

---

## 7. Reporting Workflow

### Dashboard Metrics

The dashboard provides at-a-glance operational health:

| Metric | Source | Description |
|---|---|---|
| Open Count | `sup_tickets WHERE status = 'OPEN'` | Tickets awaiting triage |
| In Progress Count | `sup_tickets WHERE status = 'IN_PROGRESS'` | Tickets being worked |
| Resolved Count | `sup_tickets WHERE status = 'RESOLVED'` | Tickets resolved pending closure |
| Closed Count | `sup_tickets WHERE status = 'CLOSED'` | Completed tickets |
| Critical Count | `sup_tickets WHERE priority = 'CRITICAL'` | Urgent tickets |
| High Count | `sup_tickets WHERE priority = 'HIGH'` | High-priority tickets |
| Escalated Count | `sup_tickets WHERE escalation_level > 0` | Escalated tickets |
| Avg Resolution | Computed from `created_at` to `resolved_at` | Mean time to resolution |

### SLA-Style Views

The ticket list supports filtering by status, priority, category, and assignee, enabling SLA monitoring:
- Filter to OPEN tickets sorted by `created_at` to identify aging tickets
- Filter to CRITICAL priority to monitor urgent items
- Filter by assignee to assess agent workload
