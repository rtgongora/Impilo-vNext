-- Search Index Definitions
CREATE TABLE srch_index_definitions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    name            VARCHAR(256)    NOT NULL,
    source_type     VARCHAR(128)    NOT NULL,
    fields_json     TEXT            NOT NULL,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_srch_index_defs_tenant ON srch_index_definitions (tenant_id);

-- Search Documents
CREATE TABLE srch_documents (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    index_id        VARCHAR(36)     NOT NULL REFERENCES srch_index_definitions(id),
    external_id     VARCHAR(256)    NOT NULL,
    title           VARCHAR(512),
    body_text       TEXT,
    metadata_json   TEXT,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_srch_docs_upsert ON srch_documents (tenant_id, index_id, external_id);
CREATE INDEX idx_srch_docs_tenant ON srch_documents (tenant_id, index_id);

-- Event Outbox (v1.1 columns)
CREATE TABLE srch_event_outbox (
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
CREATE INDEX idx_srch_outbox_unpublished ON srch_event_outbox (created_at) WHERE published_at IS NULL;

-- Idempotency Keys
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_srch_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
CREATE INDEX idx_srch_idempotency_expires ON idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;
