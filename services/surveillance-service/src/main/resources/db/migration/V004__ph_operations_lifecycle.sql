-- Public Health Operations lifecycle tables (outbreaks, field tasks, investigations, briefs)

CREATE TABLE surv.outbreak_events (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    signal_id       BIGINT          REFERENCES surv.signals(id),
    name            VARCHAR(500)    NOT NULL,
    description     TEXT,
    event_type      VARCHAR(100)    NOT NULL DEFAULT 'OUTBREAK',
    lifecycle_status VARCHAR(30)    NOT NULL DEFAULT 'RECORDED',
    severity        VARCHAR(20)     NOT NULL DEFAULT 'HIGH',
    province        VARCHAR(100),
    district        VARCHAR(100),
    facility_id     UUID,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbreak_events_tenant ON surv.outbreak_events (tenant_id);
CREATE INDEX idx_outbreak_events_status ON surv.outbreak_events (tenant_id, lifecycle_status);

CREATE TABLE surv.field_tasks (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    outbreak_id     BIGINT          REFERENCES surv.outbreak_events(id),
    case_id         BIGINT          REFERENCES surv.cases(id),
    facility_id     UUID,
    title           VARCHAR(500)    NOT NULL,
    task_type       VARCHAR(100)    NOT NULL DEFAULT 'FIELD_OPERATION',
    status          VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    assigned_to     VARCHAR(255),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    due_date        DATE,
    completed_at    TIMESTAMPTZ,
    details         JSONB           NOT NULL DEFAULT '{}',
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_field_tasks_tenant ON surv.field_tasks (tenant_id);
CREATE INDEX idx_field_tasks_status ON surv.field_tasks (tenant_id, status);

CREATE TABLE surv.investigations (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    case_id         BIGINT          REFERENCES surv.cases(id),
    outbreak_id     BIGINT          REFERENCES surv.outbreak_events(id),
    title           VARCHAR(500)    NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    findings        TEXT,
    assigned_to     VARCHAR(255),
    facility_id     UUID,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_investigations_tenant ON surv.investigations (tenant_id);
CREATE INDEX idx_investigations_status ON surv.investigations (tenant_id, status);

CREATE TABLE surv.intelligence_briefs (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    brief_type      VARCHAR(50)     NOT NULL DEFAULT 'SITUATION',
    status          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    summary_markdown TEXT,
    generated_by    VARCHAR(255)    NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_intelligence_briefs_tenant ON surv.intelligence_briefs (tenant_id);
CREATE INDEX idx_intelligence_briefs_status ON surv.intelligence_briefs (tenant_id, status);
