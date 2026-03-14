CREATE TABLE product_registry.event_outbox (
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

CREATE TABLE product_registry.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    body_hash       VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_product_registry_outbox_unpublished ON product_registry.event_outbox (created_at) WHERE published = false;
