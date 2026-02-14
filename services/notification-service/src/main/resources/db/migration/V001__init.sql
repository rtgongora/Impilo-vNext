CREATE TABLE ns_templates (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    channel         VARCHAR(32)     NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    content         TEXT            NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ns_templates_tenant ON ns_templates (tenant_id);

CREATE TABLE ns_notification_requests (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    channel         VARCHAR(32)     NOT NULL,
    to_address      VARCHAR(256)    NOT NULL,
    template_id     VARCHAR(36),
    variables_json  TEXT,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ns_requests_tenant ON ns_notification_requests (tenant_id, status);

CREATE TABLE ns_event_outbox (
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
CREATE INDEX idx_ns_outbox_unpublished ON ns_event_outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ns_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
