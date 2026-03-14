-- Jobs Service schema initialisation
CREATE SCHEMA IF NOT EXISTS jobs;

-- ─── Job Definitions ────────────────────────────────────────────────
CREATE TABLE jobs.job_definition (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    cron_expression VARCHAR(100),
    job_type        VARCHAR(50)     NOT NULL,
    config          JSONB,
    enabled         BOOLEAN         DEFAULT true,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_job_definition_tenant_id ON jobs.job_definition (tenant_id);

-- ─── Job Executions ─────────────────────────────────────────────────
CREATE TABLE jobs.job_execution (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    job_definition_id   BIGINT          NOT NULL REFERENCES jobs.job_definition(id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    result              JSONB,
    idempotency_key     VARCHAR(255),
    created_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_job_execution_tenant_id           ON jobs.job_execution (tenant_id);
CREATE INDEX idx_job_execution_job_definition_id   ON jobs.job_execution (job_definition_id);
CREATE INDEX idx_job_execution_status              ON jobs.job_execution (status);

-- ─── Event Outbox (v1.1) ───────────────────────────────────────────
CREATE TABLE jobs.event_outbox (
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

CREATE INDEX idx_event_outbox_tenant_id  ON jobs.event_outbox (tenant_id);
CREATE INDEX idx_event_outbox_published  ON jobs.event_outbox (published);

-- ─── Idempotency Keys ──────────────────────────────────────────────
CREATE TABLE jobs.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    method          VARCHAR(10)     NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    body_hash       VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);
