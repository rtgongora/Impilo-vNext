-- V021: Course registration (wraps enrolment), clinical placement + preceptor
-- sign-off, and graduation record (wraps certificate).

CREATE TABLE IF NOT EXISTS lrn_course_registration (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    student_profile_id UUID NOT NULL REFERENCES lrn_student_profile(id) ON DELETE CASCADE,
    term_id UUID NULL REFERENCES lrn_academic_term(id) ON DELETE SET NULL,
    course_id UUID NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    enrolment_id UUID NULL REFERENCES lrn_enrolment(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_course_registration UNIQUE (student_profile_id, term_id, course_id)
);

CREATE TABLE IF NOT EXISTS lrn_clinical_placement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    student_profile_id UUID NOT NULL REFERENCES lrn_student_profile(id) ON DELETE CASCADE,
    term_id UUID NULL REFERENCES lrn_academic_term(id) ON DELETE SET NULL,
    venue_id UUID NULL REFERENCES lrn_training_venue(id) ON DELETE SET NULL,
    preceptor_facilitator_id UUID NULL REFERENCES lrn_facilitator(id) ON DELETE SET NULL,
    title VARCHAR(512) NULL,
    starts_on DATE NULL,
    ends_on DATE NULL,
    signoff_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    signed_off_by VARCHAR(255) NULL,
    signed_off_at TIMESTAMPTZ NULL,
    signoff_notes TEXT NULL,
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_clinical_placement_student
    ON lrn_clinical_placement (tenant_id, student_profile_id);

CREATE TABLE IF NOT EXISTS lrn_graduation_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    student_profile_id UUID NOT NULL REFERENCES lrn_student_profile(id) ON DELETE CASCADE,
    program_id UUID NOT NULL REFERENCES lrn_academic_program(id) ON DELETE CASCADE,
    qualification_title VARCHAR(512) NULL,
    awarded_at TIMESTAMPTZ NULL,
    registry_number VARCHAR(64) NOT NULL,
    certificate_id UUID NULL REFERENCES lrn_certificate(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFERRED',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_graduation_registry UNIQUE (tenant_id, registry_number)
);
