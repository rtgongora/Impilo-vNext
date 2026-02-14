CREATE TABLE rs_rules (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    name            VARCHAR(256)    NOT NULL,
    expression      TEXT            NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_rs_rules_tenant ON rs_rules (tenant_id, enabled);

CREATE TABLE rs_decision_logs (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    rule_id         VARCHAR(36),
    outcome         VARCHAR(32)     NOT NULL,
    reason          TEXT,
    facts_json      TEXT            NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_rs_decisions_tenant ON rs_decision_logs (tenant_id, created_at DESC);

CREATE TABLE rs_event_outbox (
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
CREATE INDEX idx_rs_outbox_unpublished ON rs_event_outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_rs_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
