CREATE SCHEMA IF NOT EXISTS inventory_elmis;

CREATE TABLE inventory_elmis.sync_state (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    sync_type       VARCHAR(50)     NOT NULL,
    last_sync_at    TIMESTAMPTZ,
    status          VARCHAR(20)     DEFAULT 'IDLE',
    records_synced  INT             DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE TABLE inventory_elmis.event_outbox (
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

CREATE TABLE inventory_elmis.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    body_hash       VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_inv_elmis_sync_tenant ON inventory_elmis.sync_state (tenant_id, facility_id);
CREATE INDEX idx_inv_elmis_outbox_unpublished ON inventory_elmis.event_outbox (created_at) WHERE published = false;
