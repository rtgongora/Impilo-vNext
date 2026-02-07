-- =============================================================================
-- TSHEPO Audit Service — V001 Initial Schema
-- Append-only tamper-evident audit log with SHA-256 hash chain
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS tshepo_audit;

SET search_path TO tshepo_audit;

-- -----------------------------------------------------------------------------
-- 1. audit_event — immutable, append-only audit log entries
-- -----------------------------------------------------------------------------
CREATE TABLE tshepo_audit.audit_event (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    actor_type      VARCHAR(32)     NOT NULL,
    subject_ref     VARCHAR(255),
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(255),
    action          VARCHAR(64)     NOT NULL,
    outcome         VARCHAR(16)     NOT NULL,
    purpose_of_use  VARCHAR(32),
    facility_id     UUID,
    correlation_id  UUID            NOT NULL,
    detail          JSONB,
    previous_hash   VARCHAR(64),
    entry_hash      VARCHAR(64)     NOT NULL,
    sequence_number BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Composite index for chain traversal and tenant-scoped sequence queries
CREATE INDEX idx_audit_event_tenant_seq
    ON tshepo_audit.audit_event (tenant_id, sequence_number);

-- Index for actor-based lookups (who did what)
CREATE INDEX idx_audit_event_actor
    ON tshepo_audit.audit_event (tenant_id, actor_id);

-- Index for subject-based lookups (patient access history)
CREATE INDEX idx_audit_event_subject
    ON tshepo_audit.audit_event (tenant_id, subject_ref);

-- Prevent any UPDATE or DELETE on audit_event (append-only enforcement)
CREATE OR REPLACE FUNCTION tshepo_audit.deny_audit_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE ON tshepo_audit.audit_event
    FOR EACH ROW EXECUTE FUNCTION tshepo_audit.deny_audit_event_mutation();

CREATE TRIGGER trg_audit_event_no_delete
    BEFORE DELETE ON tshepo_audit.audit_event
    FOR EACH ROW EXECUTE FUNCTION tshepo_audit.deny_audit_event_mutation();

-- -----------------------------------------------------------------------------
-- 2. audit_chain_head — tracks current hash chain head per tenant
--    Uses SELECT ... FOR UPDATE for gapless sequencing
-- -----------------------------------------------------------------------------
CREATE TABLE tshepo_audit.audit_chain_head (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL UNIQUE,
    current_hash    VARCHAR(64)     NOT NULL,
    sequence_number BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- 3. audit_export — signed audit export bundles for legal defensibility
-- -----------------------------------------------------------------------------
CREATE TABLE tshepo_audit.audit_export (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    requested_by    VARCHAR(255)    NOT NULL,
    from_sequence   BIGINT          NOT NULL,
    to_sequence     BIGINT          NOT NULL,
    entry_count     INT             NOT NULL,
    bundle_hash     VARCHAR(64)     NOT NULL,
    signature       TEXT            NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Index for export lookups by tenant
CREATE INDEX idx_audit_export_tenant
    ON tshepo_audit.audit_export (tenant_id);
