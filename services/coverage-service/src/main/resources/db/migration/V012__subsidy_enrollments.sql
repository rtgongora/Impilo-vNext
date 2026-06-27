-- Per-member subsidy enrolment: the CPID -> subsidy-programme link.
-- Carries the member's exemption_category (INDIGENT / ELDERLY / MATERNITY / ...), the billing
-- classification COSTA charging rules key exemptions on. An active enrolment takes precedence
-- over coverage plan type when resolving a patient's billing category.

CREATE TABLE cv_subsidy_enrollments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    client_id           VARCHAR(255) NOT NULL,
    subsidy_program_id  UUID NOT NULL REFERENCES cv_subsidy_programs (id),
    exemption_category  VARCHAR(64) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_subsidy_enrol_member
    ON cv_subsidy_enrollments (tenant_id, client_id, status);
CREATE INDEX idx_cv_subsidy_enrol_program
    ON cv_subsidy_enrollments (tenant_id, subsidy_program_id);
