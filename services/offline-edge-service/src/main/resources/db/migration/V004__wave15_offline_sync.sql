-- Wave 15: Offline sync batch tracking and JWT entitlement support
-- Adds sync-specific columns and indexes for the offline-edge-service

-- Add sync_batch_id to captured actions for tracking which sync batch delivered each action
ALTER TABLE ofe_captured_actions ADD COLUMN IF NOT EXISTS sync_batch_id UUID;
CREATE INDEX IF NOT EXISTS idx_ofe_actions_sync_batch ON ofe_captured_actions (sync_batch_id);

-- Add token_id column for JWT-based entitlement reference (parallel to entitlement_id)
-- This allows linking to TSHEPO capability tokens without requiring the local entitlement record
ALTER TABLE ofe_captured_actions ADD COLUMN IF NOT EXISTS token_id UUID;
CREATE INDEX IF NOT EXISTS idx_ofe_actions_token ON ofe_captured_actions (token_id);

-- Track sync batch metadata on reconciliation batches
ALTER TABLE ofe_reconciliation_batches ADD COLUMN IF NOT EXISTS sync_source VARCHAR(64) DEFAULT 'API';
ALTER TABLE ofe_reconciliation_batches ADD COLUMN IF NOT EXISTS device_id VARCHAR(255);
ALTER TABLE ofe_reconciliation_batches ADD COLUMN IF NOT EXISTS facility_ref VARCHAR(255);

-- Add offline_actions audit table (append-only log of all offline operations)
CREATE TABLE IF NOT EXISTS ofe_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    event_type      VARCHAR(128)    NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    tenant_id       UUID            NOT NULL,
    facility_ref    VARCHAR(255),
    token_id        UUID,
    action_id       UUID,
    batch_id        UUID,
    patient_ref     VARCHAR(255),
    device_id       VARCHAR(255),
    detail_json     TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ofe_audit_actor ON ofe_audit_log (actor_id, created_at);
CREATE INDEX idx_ofe_audit_tenant ON ofe_audit_log (tenant_id, event_type);
CREATE INDEX idx_ofe_audit_token ON ofe_audit_log (token_id);

COMMENT ON TABLE ofe_audit_log IS 'Append-only audit trail for all offline operations (Wave 15)';
