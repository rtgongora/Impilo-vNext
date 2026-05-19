-- ============================================================================
-- nhume-service V001 — Initial schema
-- Provides: event_outbox (v1.1 envelope), idempotency_keys
-- Prefix: nhume_
-- ============================================================================

-- v1.1 Event Outbox (canonical schema per EVENTING_AND_TOPICS.md §2.2)
CREATE TABLE nhume_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'nhume-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    partition_key       VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_nhume_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_nhume_outbox_unpublished
    ON nhume_event_outbox (created_at)
    WHERE published_at IS NULL;
CREATE INDEX idx_nhume_outbox_aggregate
    ON nhume_event_outbox (aggregate_type, aggregate_id);

-- Idempotency keys (tenant + pod + key composite)
CREATE TABLE nhume_idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_nhume_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
