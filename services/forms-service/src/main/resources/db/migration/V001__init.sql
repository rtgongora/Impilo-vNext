-- ═══════════════════════════════════════════════════════════════════════
-- 1. Form Schemas — metadata for each form definition
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE fs_form_schemas (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    form_key        VARCHAR(128)    NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    description     TEXT,
    current_version INT             NOT NULL DEFAULT 1,
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_fs_schemas_tenant_key ON fs_form_schemas (tenant_id, form_key);
CREATE INDEX idx_fs_schemas_tenant ON fs_form_schemas (tenant_id);
CREATE INDEX idx_fs_schemas_tenant_status ON fs_form_schemas (tenant_id, status);

-- ═══════════════════════════════════════════════════════════════════════
-- 2. Form Schema Versions — immutable JSON Schema snapshots
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE fs_form_schema_versions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    form_schema_id  VARCHAR(36)     NOT NULL REFERENCES fs_form_schemas(id),
    version         INT             NOT NULL,
    schema_json     TEXT            NOT NULL,
    changelog       VARCHAR(1024),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_fs_versions_schema_ver ON fs_form_schema_versions (form_schema_id, version);
CREATE INDEX idx_fs_versions_schema ON fs_form_schema_versions (form_schema_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 3. Event Outbox (v1.1 columns) — transactional outbox for Kafka
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE fs_event_outbox (
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

CREATE INDEX idx_fs_outbox_unpublished ON fs_event_outbox (created_at)
    WHERE published_at IS NULL;

-- ═══════════════════════════════════════════════════════════════════════
-- 4. Idempotency Keys — Tech Companion auto-config uses this table
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_fs_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);
