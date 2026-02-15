-- ============================================================
-- Reporting Service  (V001 initial schema)
-- Report definition registry, run history, scheduled jobs,
-- event outbox, and idempotency keys.
-- ============================================================

-- 1. Report definitions — registry of available reports
CREATE TABLE rpt_report_definitions (
    id              BIGSERIAL       PRIMARY KEY,
    definition_id   UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    report_key      VARCHAR(100)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     VARCHAR(1000),
    query_template  TEXT            NOT NULL,
    parameters      JSONB           NOT NULL DEFAULT '{}',
    output_format   VARCHAR(20)     NOT NULL DEFAULT 'JSON',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_rpt_def_tenant_key UNIQUE (tenant_id, report_key)
);

CREATE INDEX idx_rpt_def_tenant ON rpt_report_definitions (tenant_id);
CREATE INDEX idx_rpt_def_status ON rpt_report_definitions (tenant_id, status);

-- 2. Report runs — execution history
CREATE TABLE rpt_report_runs (
    id              BIGSERIAL       PRIMARY KEY,
    run_id          UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    definition_id   BIGINT          NOT NULL REFERENCES rpt_report_definitions(id),
    parameters      JSONB           NOT NULL DEFAULT '{}',
    output_format   VARCHAR(20)     NOT NULL DEFAULT 'JSON',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    result          TEXT,
    error_message   VARCHAR(1000),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rpt_runs_tenant ON rpt_report_runs (tenant_id);
CREATE INDEX idx_rpt_runs_def ON rpt_report_runs (definition_id);
CREATE INDEX idx_rpt_runs_status ON rpt_report_runs (tenant_id, status);

-- 3. Report schedules — stub scheduled job entries
CREATE TABLE rpt_report_schedules (
    id              BIGSERIAL       PRIMARY KEY,
    schedule_id     UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    definition_id   BIGINT          NOT NULL REFERENCES rpt_report_definitions(id),
    cron_expression VARCHAR(100)    NOT NULL,
    parameters      JSONB           NOT NULL DEFAULT '{}',
    output_format   VARCHAR(20)     NOT NULL DEFAULT 'JSON',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    next_run_at     TIMESTAMPTZ,
    last_run_at     TIMESTAMPTZ,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rpt_sched_tenant ON rpt_report_schedules (tenant_id);
CREATE INDEX idx_rpt_sched_def ON rpt_report_schedules (definition_id);
CREATE INDEX idx_rpt_sched_status ON rpt_report_schedules (tenant_id, status);

-- 4. Event outbox — reliable Kafka publishing
CREATE TABLE rpt_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_rpt_outbox_unpublished ON rpt_event_outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_rpt_outbox_aggregate ON rpt_event_outbox (aggregate_type, aggregate_id);

-- 5. Idempotency keys — deduplication for v1.1 command endpoints
CREATE TABLE idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(128)    NOT NULL,
    pod_id          VARCHAR(128)    NOT NULL,
    idempotency_key VARCHAR(256)    NOT NULL,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INTEGER         NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_tenant_pod_key UNIQUE (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
