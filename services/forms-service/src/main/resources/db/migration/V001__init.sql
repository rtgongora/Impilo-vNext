CREATE SCHEMA IF NOT EXISTS forms;

-- ═══════════════════════════════════════════════════════════════════════
-- 1. Form Definitions — metadata for each clinical form
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE forms.form_definitions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    code            VARCHAR(128)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_form_defs_tenant_code ON forms.form_definitions (tenant_id, code);
CREATE INDEX idx_form_defs_tenant ON forms.form_definitions (tenant_id);
CREATE INDEX idx_form_defs_tenant_status ON forms.form_definitions (tenant_id, status);

-- ═══════════════════════════════════════════════════════════════════════
-- 2. Form Versions — immutable content snapshots (JSON with "fields" array)
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE forms.form_versions (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    form_id         VARCHAR(36)     NOT NULL REFERENCES forms.form_definitions(id),
    version_number  INT             NOT NULL,
    content_json    TEXT            NOT NULL,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_form_versions_form_ver ON forms.form_versions (form_id, version_number);
CREATE INDEX idx_form_versions_form ON forms.form_versions (form_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 3. Form Publications — records which version was published and when
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE forms.form_publications (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    form_id         VARCHAR(36)     NOT NULL REFERENCES forms.form_definitions(id),
    version_id      VARCHAR(36)     NOT NULL REFERENCES forms.form_versions(id),
    published_by    VARCHAR(255)    NOT NULL,
    published_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_form_pubs_form ON forms.form_publications (form_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 4. Event Outbox (v1.1 columns) — transactional outbox for Kafka
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE forms.event_outbox (
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

CREATE INDEX idx_forms_outbox_unpublished ON forms.event_outbox (created_at)
    WHERE published_at IS NULL;

-- ═══════════════════════════════════════════════════════════════════════
-- 5. Idempotency Keys — Tech Companion auto-config uses this table
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
    CONSTRAINT pk_forms_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_forms_idempotency_expires ON idempotency_keys (expires_at)
    WHERE expires_at IS NOT NULL;
