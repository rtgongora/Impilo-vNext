-- offline-edge-service: outbox + idempotency infrastructure
-- Table prefix: ofe_

CREATE TABLE ofe_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1',
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255),
    producer        VARCHAR(128)    NOT NULL DEFAULT 'offline-edge-service',
    tenant_id       UUID,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255),
    subject_type    VARCHAR(128),
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    payload_json    TEXT,
    partition_key   VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_ofe_outbox_unpublished ON ofe_event_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TABLE ofe_idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_hash    VARCHAR(64),
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
    CONSTRAINT uq_ofe_idempotency UNIQUE (idempotency_key)
);
