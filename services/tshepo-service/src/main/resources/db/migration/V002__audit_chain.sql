-- TSHEPO — Tamper-evident audit chain

CREATE TABLE tshepo.audit_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
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
    entry_hash      VARCHAR(64)  NOT NULL
);

CREATE INDEX idx_audit_tenant_time ON tshepo.audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_actor       ON tshepo.audit_event (tenant_id, actor_id, occurred_at DESC);
CREATE INDEX idx_audit_resource    ON tshepo.audit_event (tenant_id, resource_type, resource_id);
