-- ============================================================
-- FHIR Gateway Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS fhir_gateway;

-- ----------------------------------------------------------
-- 1. fhir_route — routing configuration for FHIR resources
-- ----------------------------------------------------------
CREATE TABLE fhir_gateway.fhir_route (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    source_system   VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(100)    NOT NULL,
    target_endpoint VARCHAR(1024)   NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fhir_route_tenant ON fhir_gateway.fhir_route (tenant_id);
CREATE INDEX idx_fhir_route_resource ON fhir_gateway.fhir_route (tenant_id, resource_type);
CREATE INDEX idx_fhir_route_enabled ON fhir_gateway.fhir_route (tenant_id, enabled);

-- ----------------------------------------------------------
-- 2. fhir_audit_log — audit trail for FHIR operations
-- ----------------------------------------------------------
CREATE TABLE fhir_gateway.fhir_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    resource_type   VARCHAR(100)    NOT NULL,
    operation       VARCHAR(20)     NOT NULL,
    source_ip       VARCHAR(45),
    actor_id        VARCHAR(255),
    outcome         VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    correlation_id  UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fhir_audit_tenant ON fhir_gateway.fhir_audit_log (tenant_id);
CREATE INDEX idx_fhir_audit_resource ON fhir_gateway.fhir_audit_log (tenant_id, resource_type);
CREATE INDEX idx_fhir_audit_operation ON fhir_gateway.fhir_audit_log (tenant_id, operation);
CREATE INDEX idx_fhir_audit_correlation ON fhir_gateway.fhir_audit_log (correlation_id);

-- ----------------------------------------------------------
-- 3. event_outbox — transactional outbox for Kafka (v1.1)
-- ----------------------------------------------------------
CREATE TABLE fhir_gateway.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(255),
    pod_id          VARCHAR(255),
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    correlation_id  VARCHAR(255),
    causation_id    VARCHAR(255),
    idempotency_key VARCHAR(255),
    schema_version  INT             NOT NULL DEFAULT 1,
    producer        VARCHAR(100)    NOT NULL DEFAULT 'fhir-gateway',
    subject_type    VARCHAR(100),
    subject_id      VARCHAR(255),
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ,
    published       BOOLEAN         NOT NULL DEFAULT false,
    publish_error   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fhir_gw_outbox_unpublished
    ON fhir_gateway.event_outbox (created_at) WHERE published = false;

-- ----------------------------------------------------------
-- 4. idempotency_keys — request dedup
-- ----------------------------------------------------------
CREATE TABLE fhir_gateway.idempotency_keys (
    tenant_id       VARCHAR(255)    NOT NULL,
    pod_id          VARCHAR(255)    NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_fhir_gw_idempotency_expires ON fhir_gateway.idempotency_keys (expires_at);
