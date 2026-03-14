-- Conflict queue
CREATE TABLE offline_sync.conflict_queue (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    sync_pack_id    BIGINT          NOT NULL REFERENCES offline_sync.sync_pack(id),
    conflict_type   VARCHAR(50)     NOT NULL,
    local_payload   JSONB           NOT NULL,
    remote_payload  JSONB,
    resolution      VARCHAR(20),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT now()
);
-- Event outbox with v1.1 columns
CREATE TABLE offline_sync.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(100)    NOT NULL,
    request_id      VARCHAR(100),
    correlation_id  VARCHAR(100),
    idempotency_key VARCHAR(255),
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         DEFAULT false,
    created_at      TIMESTAMPTZ     DEFAULT now()
);
-- Idempotency keys
CREATE TABLE offline_sync.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    body_hash       VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);
CREATE INDEX idx_offline_sync_outbox_unpublished ON offline_sync.event_outbox (created_at) WHERE published = false;
