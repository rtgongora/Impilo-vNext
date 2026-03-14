CREATE SCHEMA IF NOT EXISTS pacs;

CREATE TABLE pacs.imaging_study (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    patient_cpid    VARCHAR(100)    NOT NULL,
    study_uid       VARCHAR(255)    NOT NULL UNIQUE,
    modality        VARCHAR(20)     NOT NULL,
    description     VARCHAR(500),
    study_date      TIMESTAMPTZ     NOT NULL,
    status          VARCHAR(20)     DEFAULT 'RECEIVED',
    orthanc_id      VARCHAR(255),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE TABLE pacs.event_outbox (
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

CREATE TABLE pacs.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    body_hash       VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_pacs_study_tenant ON pacs.imaging_study (tenant_id);
CREATE INDEX idx_pacs_study_patient ON pacs.imaging_study (patient_cpid);
CREATE INDEX idx_pacs_outbox_unpublished ON pacs.event_outbox (created_at) WHERE published = false;
