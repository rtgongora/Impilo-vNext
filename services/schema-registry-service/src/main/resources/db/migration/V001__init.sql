-- schema-registry-service: self-hosted schema storage with compatibility checks
-- Table prefix: scr_

CREATE TABLE scr_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1',
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255),
    producer        VARCHAR(128)    NOT NULL DEFAULT 'schema-registry-service',
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

CREATE INDEX idx_scr_outbox_unpublished ON scr_event_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TABLE scr_idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_hash    VARCHAR(64),
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
    CONSTRAINT uq_scr_idempotency UNIQUE (idempotency_key)
);

-- Subjects (top-level grouping for schemas, e.g. "impilo.vito.patient.created.v1")
CREATE TABLE scr_subjects (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_name    VARCHAR(512)    NOT NULL UNIQUE,
    compatibility   VARCHAR(32)     NOT NULL DEFAULT 'BACKWARD',
    description     TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Schema versions (immutable once registered)
CREATE TABLE scr_schema_versions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id      UUID            NOT NULL REFERENCES scr_subjects(id),
    version         INT             NOT NULL,
    schema_json     TEXT            NOT NULL,
    fingerprint     VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_scr_subject_version UNIQUE (subject_id, version)
);

CREATE INDEX idx_scr_schema_subject ON scr_schema_versions (subject_id);
