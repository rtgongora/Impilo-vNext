-- Break-glass audit table (entity: BreakGlassEventEntity)

CREATE TABLE IF NOT EXISTS ofe_break_glass_events (
    event_id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(255)    NOT NULL,
    facility_id         VARCHAR(255)    NOT NULL,
    patient_ref         VARCHAR(255),
    reason              TEXT            NOT NULL,
    override_type       VARCHAR(64)     NOT NULL,
    original_scope      VARCHAR(512),
    extended_scope      VARCHAR(512),
    entitlement_id      UUID,
    action_id           UUID,
    activated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    review_required_by  TIMESTAMPTZ     NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by         VARCHAR(255),
    review_notes        TEXT,
    reviewed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ofe_break_glass_tenant_status
    ON ofe_break_glass_events (tenant_id, status, activated_at);

COMMENT ON TABLE ofe_break_glass_events IS 'Mandatory review trail for offline break-glass activations';
