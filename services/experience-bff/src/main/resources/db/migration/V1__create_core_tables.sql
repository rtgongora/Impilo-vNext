-- ============================================================================
-- Experience BFF — Core Schema
-- V1: Facilities, Workspaces, Report Jobs, Idempotency, Outbox
-- ============================================================================

-- Facilities table (table-style read endpoint source)
CREATE TABLE facilities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(500) NOT NULL,
    code            VARCHAR(100) NOT NULL,
    facility_type   VARCHAR(100) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    province        VARCHAR(255),
    district        VARCHAR(255),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    capabilities    JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_facilities_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_facilities_tenant ON facilities (tenant_id);
CREATE INDEX idx_facilities_status ON facilities (tenant_id, status);
CREATE INDEX idx_facilities_type ON facilities (tenant_id, facility_type);
CREATE INDEX idx_facilities_province ON facilities (tenant_id, province);

-- Workspaces table (rpc-style command endpoint target)
CREATE TABLE workspaces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL REFERENCES facilities(id),
    name            VARCHAR(500) NOT NULL,
    workspace_type  VARCHAR(100) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'INACTIVE',
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    activated_at    TIMESTAMPTZ,
    activated_by    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspaces_tenant ON workspaces (tenant_id);
CREATE INDEX idx_workspaces_facility ON workspaces (facility_id);
CREATE INDEX idx_workspaces_status ON workspaces (tenant_id, status);

-- Report jobs table (edge-function-style job trigger)
CREATE TABLE report_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    report_type     VARCHAR(100) NOT NULL,
    parameters      JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(50)  NOT NULL DEFAULT 'QUEUED',
    requested_by    VARCHAR(255) NOT NULL,
    result_url      TEXT,
    error_message   TEXT,
    queued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_report_jobs_tenant ON report_jobs (tenant_id);
CREATE INDEX idx_report_jobs_status ON report_jobs (tenant_id, status);
CREATE INDEX idx_report_jobs_requested_by ON report_jobs (tenant_id, requested_by);

-- Idempotency table (v1.1 requirement — composite PK per JdbcIdempotencyRepository)
CREATE TABLE experience_bff_idempotency (
    tenant_id           VARCHAR(255) NOT NULL,
    pod_id              VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    request_hash        VARCHAR(64) NOT NULL,
    response_status     INTEGER NOT NULL,
    response_body       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_experience_bff_idem_expires ON experience_bff_idempotency (expires_at);

-- Event outbox table (v1.1 outbox pattern)
CREATE TABLE event_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    event_type          VARCHAR(255) NOT NULL,
    schema_version      INTEGER NOT NULL DEFAULT 1,
    correlation_id      VARCHAR(255) NOT NULL,
    causation_id        VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(255),
    producer            VARCHAR(100) NOT NULL DEFAULT 'experience-bff',
    tenant_id           VARCHAR(255) NOT NULL,
    pod_id              VARCHAR(255) NOT NULL,
    subject_type        VARCHAR(100) NOT NULL,
    subject_id          VARCHAR(255) NOT NULL,
    payload             JSONB NOT NULL,
    meta                JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         TIMESTAMPTZ NOT NULL,
    emitted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    published_at        TIMESTAMPTZ,
    partition_key       VARCHAR(255) NOT NULL
);

CREATE INDEX idx_event_outbox_unpublished ON event_outbox (published, id) WHERE published = FALSE;
CREATE INDEX idx_event_outbox_tenant ON event_outbox (tenant_id);
CREATE INDEX idx_event_outbox_type ON event_outbox (event_type);
CREATE INDEX idx_event_outbox_subject ON event_outbox (subject_type, subject_id);
