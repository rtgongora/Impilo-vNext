-- Provisional / external provider access profiles for patient-mediated vNext collaboration.
-- Distinct from full varapi.provider rows: bounded temporary_provider_public_id + linkage hooks.

CREATE TABLE varapi.external_provider_access_profile (
    id                              BIGSERIAL PRIMARY KEY,
    tenant_id                       UUID        NOT NULL,
    temporary_provider_public_id    VARCHAR(26) NOT NULL UNIQUE,
    vito_access_grant_ref           VARCHAR(64) NOT NULL,
    given_name                      VARCHAR(255),
    family_name                     VARCHAR(255),
    profession_or_cadre             VARCHAR(120),
    email                           VARCHAR(255),
    phone                           VARCHAR(64),
    organisation_hint               VARCHAR(512),
    council_id                      BIGINT      NOT NULL REFERENCES varapi.councils(id),
    council_registration_number     VARCHAR(120) NOT NULL,
    self_assertion_state              VARCHAR(40) NOT NULL DEFAULT 'CAPTURED',
    verification_state              VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    trust_level                       VARCHAR(64) NOT NULL,
    matched_provider_public_id      VARCHAR(26),
    linked_provider_public_id       VARCHAR(26),
    metadata                        JSONB,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ext_prov_grant_ref
    ON varapi.external_provider_access_profile (tenant_id, vito_access_grant_ref);

CREATE INDEX idx_ext_prov_council_reg
    ON varapi.external_provider_access_profile (tenant_id, council_id, council_registration_number);

CREATE TABLE varapi.external_provider_verification_attempt (
    id                              BIGSERIAL PRIMARY KEY,
    external_profile_id             BIGINT      NOT NULL
        REFERENCES varapi.external_provider_access_profile(id),
    verification_type               VARCHAR(64) NOT NULL,
    source_type                       VARCHAR(64) NOT NULL,
    outcome                           VARCHAR(32) NOT NULL,
    confidence_score                  NUMERIC(5,2),
    matched_provider_public_id      VARCHAR(26),
    notes                             TEXT,
    requested_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ext_prov_ver_profile ON varapi.external_provider_verification_attempt (external_profile_id);
