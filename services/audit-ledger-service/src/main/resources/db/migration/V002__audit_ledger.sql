-- audit-ledger-service: append-only tamper-evident audit records
-- Table prefix: ald_
-- IMMUTABILITY ENFORCED: trigger prevents UPDATE/DELETE

CREATE TABLE ald_audit_records (
    id              BIGSERIAL       PRIMARY KEY,
    record_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    sequence_num    BIGINT          NOT NULL,
    correlation_id  UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    actor_type      VARCHAR(64)     NOT NULL,
    action          VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(128)    NOT NULL,
    resource_id     VARCHAR(255),
    outcome         VARCHAR(32)     NOT NULL DEFAULT 'SUCCESS',
    detail_json     TEXT,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    prev_hash       VARCHAR(64),
    entry_hash      VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_ald_record UNIQUE (record_id),
    CONSTRAINT uq_ald_chain_link UNIQUE (tenant_id, prev_hash),
    CONSTRAINT uq_ald_sequence UNIQUE (tenant_id, sequence_num)
);

CREATE INDEX idx_ald_records_tenant ON ald_audit_records (tenant_id, sequence_num);
CREATE INDEX idx_ald_records_correlation ON ald_audit_records (correlation_id);
CREATE INDEX idx_ald_records_actor ON ald_audit_records (actor_id, occurred_at DESC);
CREATE INDEX idx_ald_records_resource ON ald_audit_records (resource_type, resource_id);

-- Chain head tracker: one row per tenant, tracks latest hash + sequence
CREATE TABLE ald_chain_heads (
    tenant_id       UUID            PRIMARY KEY,
    current_hash    VARCHAR(64)     NOT NULL,
    current_seq     BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Immutability enforcement: prevent UPDATE/DELETE on audit records
CREATE OR REPLACE FUNCTION ald_prevent_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are immutable: % operations are forbidden', TG_OP;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ald_no_update
    BEFORE UPDATE ON ald_audit_records
    FOR EACH ROW EXECUTE FUNCTION ald_prevent_mutation();

CREATE TRIGGER trg_ald_no_delete
    BEFORE DELETE ON ald_audit_records
    FOR EACH ROW EXECUTE FUNCTION ald_prevent_mutation();
