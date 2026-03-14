-- ============================================================
-- BUTANO FHIR Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS butano_fhir;

-- ----------------------------------------------------------
-- 1. fhir_resource — FHIR resource storage
-- ----------------------------------------------------------
CREATE TABLE butano_fhir.fhir_resource (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    resource_type   VARCHAR(100)    NOT NULL,
    resource_id     VARCHAR(255)    NOT NULL,
    fhir_version    VARCHAR(20)     NOT NULL DEFAULT 'R4',
    payload         JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fhir_resource_tenant ON butano_fhir.fhir_resource (tenant_id);
CREATE INDEX idx_fhir_resource_type ON butano_fhir.fhir_resource (tenant_id, resource_type);
CREATE UNIQUE INDEX idx_fhir_resource_unique ON butano_fhir.fhir_resource (tenant_id, resource_type, resource_id);

-- ----------------------------------------------------------
-- 2. event_outbox — transactional outbox for Kafka (v1.1)
-- ----------------------------------------------------------
CREATE TABLE butano_fhir.event_outbox (
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
    producer        VARCHAR(100)    NOT NULL DEFAULT 'butano-fhir',
    subject_type    VARCHAR(100),
    subject_id      VARCHAR(255),
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    publish_error   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_butano_fhir_outbox_unpublished
    ON butano_fhir.event_outbox (created_at) WHERE published = FALSE;

-- ----------------------------------------------------------
-- 3. idempotency_keys — request dedup (v1.1)
-- ----------------------------------------------------------
CREATE TABLE butano_fhir.idempotency_keys (
    tenant_id       VARCHAR(255)    NOT NULL,
    pod_id          VARCHAR(255)    NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
