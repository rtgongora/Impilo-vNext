-- Wave 8: Conflict review queue for offline replay conflicts
CREATE TABLE IF NOT EXISTS ofe_conflict_reviews (
    conflict_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    action_id           UUID            NOT NULL REFERENCES ofe_captured_actions(action_id),
    batch_id            UUID,
    tenant_id           UUID            NOT NULL,
    patient_ref         VARCHAR(255)    NOT NULL,
    action_type         VARCHAR(64)     NOT NULL,
    conflict_reason     TEXT            NOT NULL,
    offline_payload     TEXT,
    existing_resource_ref VARCHAR(255),
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    resolved_by         VARCHAR(255),
    resolution_notes    TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_ofe_conflicts_tenant_status ON ofe_conflict_reviews (tenant_id, status);
CREATE INDEX idx_ofe_conflicts_action ON ofe_conflict_reviews (action_id);

COMMENT ON TABLE ofe_conflict_reviews IS 'Review queue for offline replay conflicts (Class C reconciliation)';
