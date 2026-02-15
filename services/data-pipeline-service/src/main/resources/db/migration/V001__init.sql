-- =============================================
-- Service : data-pipeline-service
-- Flyway  : V001__init.sql
-- Purpose : Complete pipeline schema — 5 tables
-- =============================================

-- ─── 1. dp_ingestion_sources ────────────────────────────────────────────────────
-- Registered event sources that the pipeline accepts events from.

CREATE TABLE dp_ingestion_sources (
    source_id       VARCHAR(64)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    display_name    VARCHAR(255)    NOT NULL,
    description     TEXT,
    source_type     VARCHAR(40)     NOT NULL DEFAULT 'SERVICE',
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dp_sources_tenant ON dp_ingestion_sources (tenant_id);
CREATE INDEX idx_dp_sources_tenant_type ON dp_ingestion_sources (tenant_id, source_type);

-- ─── 2. dp_ingested_events ──────────────────────────────────────────────────────
-- Curated pipeline records derived from v1.1 EventEnvelope payloads.

CREATE TABLE dp_ingested_events (
    event_id        VARCHAR(64)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    source_id       VARCHAR(64)     NOT NULL REFERENCES dp_ingestion_sources(source_id),
    event_type      VARCHAR(128)    NOT NULL,
    aggregate_type  VARCHAR(128),
    aggregate_id    VARCHAR(128),
    occurred_at     TIMESTAMPTZ     NOT NULL,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    pod_id          VARCHAR(64)     NOT NULL,
    correlation_id  VARCHAR(128),
    causation_id    VARCHAR(128),
    payload         JSONB           NOT NULL,
    schema_version  VARCHAR(20)     NOT NULL DEFAULT '1.1',
    ingestion_status VARCHAR(20)    NOT NULL DEFAULT 'ACCEPTED'
);

CREATE INDEX idx_dp_events_tenant_source ON dp_ingested_events (tenant_id, source_id);
CREATE INDEX idx_dp_events_tenant_type ON dp_ingested_events (tenant_id, event_type);
CREATE INDEX idx_dp_events_aggregate ON dp_ingested_events (tenant_id, aggregate_type, aggregate_id);
CREATE INDEX idx_dp_events_occurred ON dp_ingested_events (tenant_id, occurred_at);
CREATE INDEX idx_dp_events_received ON dp_ingested_events (received_at);

-- ─── 3. dp_pipeline_watermarks ──────────────────────────────────────────────────
-- Per-source high-water marks for exactly-once/idempotent replay tracking.

CREATE TABLE dp_pipeline_watermarks (
    watermark_id    BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    source_id       VARCHAR(64)     NOT NULL REFERENCES dp_ingestion_sources(source_id),
    last_event_id   VARCHAR(64)     NOT NULL,
    last_occurred_at TIMESTAMPTZ    NOT NULL,
    event_count     BIGINT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, source_id)
);

CREATE INDEX idx_dp_watermarks_tenant ON dp_pipeline_watermarks (tenant_id);

-- ─── 4. dp_event_outbox ─────────────────────────────────────────────────────────
-- Transactional outbox for reliable Kafka event publishing.

CREATE TABLE dp_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_dp_outbox_unpublished ON dp_event_outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_dp_outbox_tenant_ts ON dp_event_outbox (tenant_id, created_at);

-- ─── 5. idempotency_keys ────────────────────────────────────────────────────────
-- v1.1 idempotency support for POST/PUT/PATCH commands.

CREATE TABLE idempotency_keys (
    tenant_id       TEXT            NOT NULL,
    pod_id          TEXT            NOT NULL,
    idempotency_key TEXT            NOT NULL,
    request_hash    TEXT            NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
