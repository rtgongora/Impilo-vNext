-- Comments
CREATE TABLE sup_ticket_comments (
    comment_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES sup_tickets(ticket_id),
    tenant_id   UUID NOT NULL,
    author_ref  VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sup_comments_ticket ON sup_ticket_comments(ticket_id);

-- Escalation fields on tickets
ALTER TABLE sup_tickets ADD COLUMN escalation_level INT NOT NULL DEFAULT 0;
ALTER TABLE sup_tickets ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE sup_tickets ADD COLUMN escalated_by VARCHAR(255);
ALTER TABLE sup_tickets ADD COLUMN sla_breached_at TIMESTAMPTZ;

-- Assignments
CREATE TABLE sup_ticket_assignments (
    assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id     UUID NOT NULL REFERENCES sup_tickets(ticket_id),
    tenant_id     UUID NOT NULL,
    assignee_ref  VARCHAR(255) NOT NULL,
    assigned_by   VARCHAR(255) NOT NULL,
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    unassigned_at TIMESTAMPTZ
);
CREATE INDEX idx_sup_assignments_ticket ON sup_ticket_assignments(ticket_id);

-- Messages
CREATE TABLE sup_support_messages (
    message_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES sup_tickets(ticket_id),
    tenant_id   UUID NOT NULL,
    sender_ref  VARCHAR(255) NOT NULL,
    sender_type VARCHAR(32) NOT NULL DEFAULT 'AGENT',
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sup_messages_ticket ON sup_support_messages(ticket_id);

-- Indexes for search
CREATE INDEX idx_sup_tickets_category ON sup_tickets(category);
CREATE INDEX idx_sup_tickets_assignee ON sup_tickets(assignee_ref);
CREATE INDEX idx_sup_tickets_escalation ON sup_tickets(escalation_level);
