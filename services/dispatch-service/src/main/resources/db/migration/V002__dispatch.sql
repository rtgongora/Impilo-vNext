-- ============================================================================
-- dispatch-service V002 — Dispatch jobs and events
-- Provides: dsp_dispatch_jobs, dsp_dispatch_events
-- ============================================================================

CREATE TABLE dsp_dispatch_jobs (
    job_id                  UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    facility_ref            VARCHAR(255)    NOT NULL,
    request_ref             VARCHAR(255),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'NEW',
    assigned_agent_ref      VARCHAR(255),
    assigned_vehicle_ref    VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_jobs_tenant_id ON dsp_dispatch_jobs (tenant_id);
CREATE INDEX idx_dsp_jobs_facility_ref ON dsp_dispatch_jobs (facility_ref);
CREATE INDEX idx_dsp_jobs_status ON dsp_dispatch_jobs (status);

CREATE TABLE dsp_dispatch_events (
    id                      BIGSERIAL       PRIMARY KEY,
    job_id                  UUID            NOT NULL REFERENCES dsp_dispatch_jobs(job_id),
    status                  VARCHAR(32)     NOT NULL,
    notes_json              JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_events_job_id ON dsp_dispatch_events (job_id);
