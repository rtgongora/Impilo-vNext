-- V024: Learning-provider request → review → accredit → provision workflow.
--
-- The application is kind-aware (INDIVIDUAL/ORGANISATION/FACILITY) and, on
-- accreditation, provisions a lrn_learning_provider + an accreditation record routed
-- to the regulator matching the kind. Reuses the certificate-lifecycle/expiry idiom.

CREATE TABLE IF NOT EXISTS lrn_learning_space_application (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_kind VARCHAR(32) NOT NULL,
    requester_subject_type VARCHAR(64) NULL,
    requester_subject_id VARCHAR(255) NULL,
    requested_name VARCHAR(512) NOT NULL,
    target_identity_ref VARCHAR(255) NULL,
    disciplines_json TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    decision_by VARCHAR(255) NULL,
    decision_at TIMESTAMPTZ NULL,
    decision_notes TEXT NULL,
    provisioned_provider_id UUID NULL REFERENCES lrn_learning_provider(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_space_application_status
    ON lrn_learning_space_application (tenant_id, status);

CREATE TABLE IF NOT EXISTS lrn_provider_accreditation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_id UUID NOT NULL REFERENCES lrn_learning_provider(id) ON DELETE CASCADE,
    regulator VARCHAR(255) NOT NULL,
    certificate_ref VARCHAR(255) NULL,
    valid_until TIMESTAMPTZ NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    accredited_by VARCHAR(255) NULL,
    accredited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_provider_accreditation_provider
    ON lrn_provider_accreditation (tenant_id, provider_id);
