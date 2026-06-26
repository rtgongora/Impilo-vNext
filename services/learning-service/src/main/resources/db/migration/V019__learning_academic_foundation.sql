-- V019: Academic foundation for pre-service education.
--
-- A program is the qualification offered (diploma/degree) — distinct from a course.
-- A term is the academic calendar window with its registration period.

CREATE TABLE IF NOT EXISTS lrn_academic_program (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(160) NOT NULL,
    title VARCHAR(512) NOT NULL,
    qualification_level VARCHAR(64) NULL,
    duration_terms INT NULL,
    cadre_target VARCHAR(120) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_academic_program_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS lrn_academic_term (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    program_id UUID NULL REFERENCES lrn_academic_program(id) ON DELETE SET NULL,
    name VARCHAR(160) NOT NULL,
    academic_year VARCHAR(16) NULL,
    term_no INT NULL,
    starts_on DATE NULL,
    ends_on DATE NULL,
    registration_opens_at TIMESTAMPTZ NULL,
    registration_closes_at TIMESTAMPTZ NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_academic_term_program ON lrn_academic_term (tenant_id, program_id);
