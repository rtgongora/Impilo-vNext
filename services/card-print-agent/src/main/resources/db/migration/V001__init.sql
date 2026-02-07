-- =============================================================================
-- Card Print Agent — Initial Schema
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS card_print;

CREATE TABLE card_print.print_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    job_type VARCHAR(50) NOT NULL,  -- PROVIDER_CARD|CLIENT_CARD|FACILITY_BADGE|SHARE_SLIP|EMERGENCY_CAPSULE
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    subject_name VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',  -- QUEUED|RENDERING|RENDERED|PRINTING|PRINTED|COLLECTED|FAILED|CANCELLED
    template_name VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,  -- template data
    rendered_pdf_document_id UUID,  -- reference to landela-adapter
    qr_assertion TEXT,  -- signed QR data
    priority INTEGER NOT NULL DEFAULT 5,
    requested_by VARCHAR(255) NOT NULL,
    printed_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ,
    collected_by VARCHAR(255),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_tenant_status ON card_print.print_jobs(tenant_id, status);
CREATE INDEX idx_jobs_subject ON card_print.print_jobs(subject_type, subject_id);
CREATE INDEX idx_jobs_created ON card_print.print_jobs(created_at DESC);

CREATE TABLE card_print.print_audit (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    details JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE card_print.event_outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
