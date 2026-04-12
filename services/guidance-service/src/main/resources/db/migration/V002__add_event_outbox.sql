-- =============================================================================
-- guidance-service V002 — Companion-style transactional event outbox
--
-- Replaces the legacy V001 outbox shape (UUID PK, simplified columns) with the
-- shared-kernel CompanionOutboxPublisher contract (BIGSERIAL, idempotency, v1.1).
-- =============================================================================

DROP TABLE IF EXISTS guidance.event_outbox;

CREATE TABLE guidance.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'guidance-service',
    tenant_id       VARCHAR(255) NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_guidance_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_guidance_outbox_unpublished ON guidance.event_outbox (created_at) WHERE published_at IS NULL;
