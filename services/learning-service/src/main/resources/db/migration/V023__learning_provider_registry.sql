-- V023: Learning-provider & academy registry (3 regulated provider kinds).
--
-- A learning provider is a BINDING to an identity owned by another SoR — never a
-- parallel identity registry:
--   INDIVIDUAL   -> varapi provider/professional (+ council)
--   ORGANISATION -> workforce-governance Organisation (training_institution)
--   FACILITY     -> TUSO facility (/ Indawo place)
-- A learning space (academy/school/training-centre) binds to a governance
-- OrganisationUnit and is owned by a provider.

CREATE TABLE IF NOT EXISTS lrn_learning_provider (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_kind VARCHAR(32) NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    sor_ref VARCHAR(255) NULL,         -- kind-appropriate identity id in the owning SoR
    council_ref VARCHAR(255) NULL,     -- INDIVIDUAL: professional council registration
    disciplines_json TEXT NULL,
    branding_ref VARCHAR(1024) NULL,
    accreditation_status VARCHAR(32) NOT NULL DEFAULT 'UNACCREDITED',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_learning_provider_kind ON lrn_learning_provider (tenant_id, provider_kind, status);

CREATE TABLE IF NOT EXISTS lrn_learning_space (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_id UUID NOT NULL REFERENCES lrn_learning_provider(id) ON DELETE CASCADE,
    org_unit_ref VARCHAR(255) NULL,    -- workforce-governance OrganisationUnit id (the academy)
    name VARCHAR(512) NOT NULL,
    space_kind VARCHAR(32) NOT NULL DEFAULT 'ACADEMY',
    disciplines_json TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_learning_space_provider ON lrn_learning_space (tenant_id, provider_id);
