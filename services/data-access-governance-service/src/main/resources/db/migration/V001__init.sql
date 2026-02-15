-- ============================================================
-- DAGS — Data Access Governance Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS dags;

-- ----------------------------------------------------------
-- 1. policies — simple rule registry
-- ----------------------------------------------------------
CREATE TABLE dags.policies (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    resource_type   VARCHAR(100)    NOT NULL,
    conditions      JSONB           NOT NULL DEFAULT '{}',
    effect          VARCHAR(20)     NOT NULL DEFAULT 'DENY',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_policies_tenant ON dags.policies (tenant_id);
CREATE INDEX idx_policies_resource_type ON dags.policies (tenant_id, resource_type);

-- ----------------------------------------------------------
-- 2. access_requests — data access request workflow
-- ----------------------------------------------------------
CREATE TABLE dags.access_requests (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    requester_id    VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(100)    NOT NULL,
    resource_id     VARCHAR(255),
    purpose         VARCHAR(500)    NOT NULL,
    justification   TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    permit_token    VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_requests_tenant ON dags.access_requests (tenant_id);
CREATE INDEX idx_access_requests_status ON dags.access_requests (tenant_id, status);
CREATE INDEX idx_access_requests_requester ON dags.access_requests (tenant_id, requester_id);

-- ----------------------------------------------------------
-- 3. access_decisions — approval/denial records
-- ----------------------------------------------------------
CREATE TABLE dags.access_decisions (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    request_id      BIGINT          NOT NULL REFERENCES dags.access_requests(id),
    decision        VARCHAR(20)     NOT NULL,
    decided_by      VARCHAR(255)    NOT NULL,
    reason          TEXT,
    policy_ids      JSONB           DEFAULT '[]',
    decided_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_decisions_request ON dags.access_decisions (request_id);
CREATE INDEX idx_access_decisions_tenant ON dags.access_decisions (tenant_id);

-- ----------------------------------------------------------
-- 4. audit_log — immutable decision audit trail
-- ----------------------------------------------------------
CREATE TABLE dags.audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    action          VARCHAR(100)    NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     VARCHAR(255),
    details         JSONB           NOT NULL DEFAULT '{}',
    correlation_id  UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant ON dags.audit_log (tenant_id);
CREATE INDEX idx_audit_log_action ON dags.audit_log (tenant_id, action);
CREATE INDEX idx_audit_log_actor ON dags.audit_log (tenant_id, actor_id);

-- ----------------------------------------------------------
-- 5. event_outbox — transactional outbox for Kafka
-- ----------------------------------------------------------
CREATE TABLE dags.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       VARCHAR(255),
    pod_id          VARCHAR(255),
    correlation_id  VARCHAR(255),
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_dags_outbox_unpublished
    ON dags.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 6. idempotency_keys — request dedup
-- ----------------------------------------------------------
CREATE TABLE dags.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dags_idempotency_key ON dags.idempotency_keys (idempotency_key);
