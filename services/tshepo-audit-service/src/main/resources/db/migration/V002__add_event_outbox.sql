-- =============================================================================
-- TSHEPO Audit Service — V002 Event Outbox (v1.1 transactional outbox)
-- =============================================================================

CREATE TABLE tshepo_audit.event_outbox (
    id                 BIGSERIAL PRIMARY KEY,
    event_id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type     VARCHAR(64)     NOT NULL,
    aggregate_id       VARCHAR(255)    NOT NULL,
    event_type         VARCHAR(128)    NOT NULL,
    schema_version     INT             NOT NULL DEFAULT 1,
    correlation_id     UUID,
    causation_id       UUID,
    idempotency_key    VARCHAR(255)    NOT NULL,
    producer           VARCHAR(64)     NOT NULL DEFAULT 'tshepo-audit-service',
    tenant_id          UUID            NOT NULL,
    pod_id             VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id         VARCHAR(255)    NOT NULL,
    subject_type       VARCHAR(64)     NOT NULL,
    partition_key      VARCHAR(255),
    occurred_at        TIMESTAMPTZ     NOT NULL,
    payload_json       JSONB           NOT NULL,
    publish_error      TEXT,
    retry_count        INT             DEFAULT 0,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at       TIMESTAMPTZ,
    CONSTRAINT uq_event_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_event_outbox_unpublished_created_at
    ON tshepo_audit.event_outbox (created_at)
    WHERE published_at IS NULL;
