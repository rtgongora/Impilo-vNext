-- =============================================================================
-- khuluma-service V003 — Escalation, routing & SLA (Phase 5 / W4, G-KH-01)
--
-- A conversation/feedback item can be escalated to a queue with a priority and an
-- SLA (first-response + resolution targets). A scheduled checker marks breaches and
-- emits events. SLA targets default by priority; a tenant may override per priority.
-- =============================================================================

CREATE TABLE khuluma_sla_policy (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    priority               VARCHAR(16) NOT NULL,          -- LOW | MEDIUM | HIGH | URGENT
    first_response_minutes INT  NOT NULL,
    resolution_minutes     INT  NOT NULL,
    active                 BOOLEAN NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_sla_policy UNIQUE (tenant_id, priority)
);

CREATE TABLE khuluma_escalation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    conversation_id         UUID,                          -- the conversation/feedback being escalated
    reason                  TEXT,
    priority                VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    tier                    INT NOT NULL DEFAULT 1,         -- bumped on re-escalation
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN|ASSIGNED|ACCEPTED|RESOLVED|BREACHED|CANCELLED
    assigned_to             VARCHAR(255),
    first_response_due_at   TIMESTAMPTZ,
    resolution_due_at       TIMESTAMPTZ,
    first_responded_at      TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    first_response_breached BOOLEAN NOT NULL DEFAULT false,
    resolution_breached     BOOLEAN NOT NULL DEFAULT false,
    resolution_note         TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Operations queue: open escalations, highest priority + oldest first.
CREATE INDEX idx_khuluma_escalation_queue
    ON khuluma_escalation (tenant_id, status, priority, created_at);
-- SLA sweep: open, not-yet-resolution-breached escalations whose target has passed.
CREATE INDEX idx_khuluma_escalation_sla
    ON khuluma_escalation (resolution_breached, resolution_due_at);
