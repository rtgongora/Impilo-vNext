-- V016: Promote learning-delivery infrastructure to first-class entities.
--
-- Facilitator (trainer/teacher/preceptor/guest), training venue/site, and the
-- cohort<->facilitator link. Sessions gain nullable facilitator_id / venue_id FKs
-- ALONGSIDE the legacy free-text facilitator / location_ref columns (dual-write,
-- read prefers the FK) so existing /sessions endpoints never break.

CREATE TABLE IF NOT EXISTS lrn_facilitator (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NULL,
    subject_id VARCHAR(255) NULL,
    display_name VARCHAR(255) NOT NULL,
    facilitator_kind VARCHAR(32) NOT NULL DEFAULT 'TRAINER',
    cadre VARCHAR(120) NULL,
    email VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_facilitator_tenant ON lrn_facilitator (tenant_id, status);

CREATE TABLE IF NOT EXISTS lrn_training_venue (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    venue_kind VARCHAR(32) NOT NULL DEFAULT 'PHYSICAL',
    facility_level VARCHAR(64) NULL,
    location_ref VARCHAR(1024) NULL,
    capacity INT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_training_venue_tenant ON lrn_training_venue (tenant_id, status);

CREATE TABLE IF NOT EXISTS lrn_cohort_facilitator (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cohort_id UUID NOT NULL REFERENCES lrn_learning_cohort(id) ON DELETE CASCADE,
    facilitator_id UUID NOT NULL REFERENCES lrn_facilitator(id) ON DELETE RESTRICT,
    role_in_cohort VARCHAR(32) NOT NULL DEFAULT 'LEAD',
    assigned_by VARCHAR(255) NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_cohort_facilitator UNIQUE (cohort_id, facilitator_id, role_in_cohort)
);

ALTER TABLE lrn_scheduled_learning_session
    ADD COLUMN IF NOT EXISTS facilitator_id UUID NULL REFERENCES lrn_facilitator(id) ON DELETE SET NULL;
ALTER TABLE lrn_scheduled_learning_session
    ADD COLUMN IF NOT EXISTS venue_id UUID NULL REFERENCES lrn_training_venue(id) ON DELETE SET NULL;
