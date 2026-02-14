CREATE TABLE ih_route_definitions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    source_service  VARCHAR(128)    NOT NULL,
    event_type_prefix VARCHAR(256)  NOT NULL,
    target_service  VARCHAR(128)    NOT NULL,
    target_url      VARCHAR(512)    NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_routes_tenant ON ih_route_definitions (tenant_id);

CREATE TABLE ih_event_outbox (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    correlation_id  VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(128),
    event_type      VARCHAR(256)    NOT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    payload_json    TEXT            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_outbox_unpublished ON ih_event_outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ih_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
