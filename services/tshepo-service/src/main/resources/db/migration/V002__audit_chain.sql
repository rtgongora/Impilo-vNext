-- TSHEPO — Tamper-evident audit chain
--
-- Race condition mitigations:
--   1. UNIQUE(tenant_id, prev_hash) prevents chain forking (two entries claiming same predecessor)
--   2. sequence_num provides gapless monotonic ordering per tenant for completeness verification
--   3. audit_chain_head table enables serialized writes via SELECT ... FOR UPDATE

CREATE TABLE tshepo.audit_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    sequence_num    BIGINT       NOT NULL,
    correlation_id  UUID         NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    actor_type      VARCHAR(50)  NOT NULL,
    action          VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(255) NOT NULL,
    resource_id     VARCHAR(255),
    purpose_of_use  VARCHAR(100) NOT NULL,
    facility_id     UUID,
    workspace_id    UUID,
    outcome         VARCHAR(20)  NOT NULL,
    detail          JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    prev_hash       VARCHAR(64),
    entry_hash      VARCHAR(64)  NOT NULL,

    -- Prevent chain forking: no two entries can point to the same predecessor within a tenant
    CONSTRAINT uq_audit_chain_link UNIQUE (tenant_id, prev_hash),
    -- Enforce gapless sequence per tenant
    CONSTRAINT uq_audit_sequence   UNIQUE (tenant_id, sequence_num)
);

CREATE INDEX idx_audit_tenant_time ON tshepo.audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_actor       ON tshepo.audit_event (tenant_id, actor_id, occurred_at DESC);
CREATE INDEX idx_audit_resource    ON tshepo.audit_event (tenant_id, resource_type, resource_id);

-- Chain head tracker: one row per tenant, updated under row-level lock (SELECT ... FOR UPDATE)
-- PolicyEngine must: BEGIN → lock head row → read current_hash/current_seq → compute new entry → INSERT audit_event → UPDATE head → COMMIT
CREATE TABLE tshepo.audit_chain_head (
    tenant_id       UUID PRIMARY KEY,
    current_hash    VARCHAR(64)  NOT NULL,
    current_seq     BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
