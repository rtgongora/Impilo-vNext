-- V020: Pre-service admission lifecycle — application + academic student profile.
--
-- student_profile is the academic identity (distinct from lrn_user_learning_profile,
-- which is the in-service learner/CPD record). Link by subject_type/subject_id.

CREATE TABLE IF NOT EXISTS lrn_student_application (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    program_id UUID NOT NULL REFERENCES lrn_academic_program(id) ON DELETE CASCADE,
    term_id UUID NULL REFERENCES lrn_academic_term(id) ON DELETE SET NULL,
    applicant_subject_type VARCHAR(64) NULL,
    applicant_subject_id VARCHAR(255) NULL,
    applicant_name VARCHAR(255) NOT NULL,
    applicant_contact_json TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    decision_by VARCHAR(255) NULL,
    decision_at TIMESTAMPTZ NULL,
    decision_notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_student_application_program
    ON lrn_student_application (tenant_id, program_id, status);

CREATE TABLE IF NOT EXISTS lrn_student_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NULL,
    subject_id VARCHAR(255) NULL,
    student_number VARCHAR(64) NOT NULL,
    program_id UUID NOT NULL REFERENCES lrn_academic_program(id) ON DELETE CASCADE,
    admission_application_id UUID NULL REFERENCES lrn_student_application(id) ON DELETE SET NULL,
    admitted_at TIMESTAMPTZ NULL,
    current_term_id UUID NULL REFERENCES lrn_academic_term(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ADMITTED',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_student_profile_number UNIQUE (tenant_id, student_number)
);
CREATE INDEX IF NOT EXISTS idx_lrn_student_profile_program
    ON lrn_student_profile (tenant_id, program_id, status);
