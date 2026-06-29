-- =============================================================================
-- guidance-service V003 — Nompilo AI→human handoff lifecycle (G-KH-05/06)
--
-- The handoff contract types (NompiloSurfaceType.HUMAN_HANDOFF, NompiloHandoffOption,
-- core.nompilo.handoff.requested) existed but no service persisted or orchestrated the
-- lifecycle — the BFF entry point only echoed "ACCEPTED". This table backs a real
-- queue→accept→escalate→close lifecycle, owned by guidance-service (the Nompilo SoR).
-- =============================================================================

CREATE TABLE guidance.nompilo_handoff (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    transaction_id  VARCHAR(255),               -- the visit / core-transaction the handoff arose from
    subject_id      VARCHAR(255),               -- the person the conversation is about
    actor_id        VARCHAR(255),               -- who triggered the handoff (the person in the AI chat)
    reason          TEXT,
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',   -- LOW | MEDIUM | HIGH | URGENT
    destination     VARCHAR(32)  NOT NULL DEFAULT 'CARE_TEAM',-- CARE_TEAM | FACILITY_HELP_DESK | CALL_CENTRE | OPERATIONS_DESK
    flow_id         VARCHAR(255),               -- the Nompilo flow/conversation id for continuity
    context_json    JSONB,                      -- opaque conversation context handed to the human
    status          VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',   -- QUEUED | ACCEPTED | ESCALATED | CLOSED | CANCELLED
    assigned_to     VARCHAR(255),               -- the human who accepted it
    close_note      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ
);

-- Operations desk queue: open handoffs for a tenant, highest priority + oldest first.
CREATE INDEX idx_nompilo_handoff_queue
    ON guidance.nompilo_handoff (tenant_id, status, priority, created_at);
