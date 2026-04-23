-- =============================================
-- Varapi V009 — Council regulatory domain + per-council configuration
-- =============================================

-- Per-council JSON configuration (fees, CPD thresholds, workflow hints, document requirements)
CREATE TABLE IF NOT EXISTS varapi.council_regulatory_configs (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    council_id          BIGINT      NOT NULL REFERENCES varapi.councils(id),
    config_version      INTEGER     NOT NULL DEFAULT 1,
    fees_json           JSONB,
    cpd_rules_json      JSONB,
    workflow_hints_json JSONB,
    document_requirements_json JSONB,
    workflow_template_code VARCHAR(80),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, council_id)
);

CREATE INDEX IF NOT EXISTS idx_council_regulatory_configs_tenant
    ON varapi.council_regulatory_configs(tenant_id);

-- Council-scoped registration record (distinct from generic licenses table)
CREATE TABLE IF NOT EXISTS varapi.provider_council_registration_records (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT      NOT NULL REFERENCES varapi.councils(id),
    registration_number     VARCHAR(120),
    registration_category   VARCHAR(80),
    registration_date       DATE,
    status                  VARCHAR(40) NOT NULL DEFAULT 'REGISTERED',
    issued_under_authority  VARCHAR(255),
    expiry_date             DATE,
    source_reference          VARCHAR(255),
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_reg_provider ON varapi.provider_council_registration_records(provider_id);
CREATE INDEX IF NOT EXISTS idx_pcouncil_reg_council ON varapi.provider_council_registration_records(council_id);

-- Council annual / practising licence history
CREATE TABLE IF NOT EXISTS varapi.provider_council_licence_records (
    id                          BIGSERIAL PRIMARY KEY,
    tenant_id                   UUID        NOT NULL,
    provider_id                 BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id                  BIGINT      NOT NULL REFERENCES varapi.councils(id),
    licence_type                VARCHAR(80) NOT NULL,
    issue_date                  DATE        NOT NULL,
    expiry_date                 DATE,
    status                      VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    restriction_summary         TEXT,
    issued_under_authority      VARCHAR(255),
    supersedes_licence_id       BIGINT REFERENCES varapi.provider_council_licence_records(id),
    certificate_artifact_reference VARCHAR(512),
    metadata                    JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_lic_provider ON varapi.provider_council_licence_records(provider_id);

-- Specialty / category recognition under a council
CREATE TABLE IF NOT EXISTS varapi.provider_council_specialty_records (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT      NOT NULL REFERENCES varapi.councils(id),
    recognition_type        VARCHAR(60) NOT NULL,
    specialty_or_category_code VARCHAR(80) NOT NULL,
    recognition_date        DATE,
    status                  VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    expiry_date             DATE,
    notes                   TEXT,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_spec_provider ON varapi.provider_council_specialty_records(provider_id);

-- Council-scoped CPD obligation profile (aggregates / targets; events remain in cpd_* tables)
CREATE TABLE IF NOT EXISTS varapi.provider_council_cpd_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT      NOT NULL REFERENCES varapi.councils(id),
    cycle_start             DATE        NOT NULL,
    cycle_end               DATE        NOT NULL,
    required_credits_or_units INTEGER     NOT NULL DEFAULT 0,
    earned_credits_or_units INTEGER     NOT NULL DEFAULT 0,
    compliance_status       VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    last_calculated_at      TIMESTAMPTZ,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider_id, council_id, cycle_start)
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_cpd_provider ON varapi.provider_council_cpd_profiles(provider_id);

-- Council application / lifecycle documents
CREATE TABLE IF NOT EXISTS varapi.provider_council_documents (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    application_id          BIGINT REFERENCES varapi.provider_applications(id),
    council_id              BIGINT REFERENCES varapi.councils(id),
    document_type           VARCHAR(80) NOT NULL,
    file_reference          VARCHAR(512) NOT NULL,
    verification_state      VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    uploaded_by             VARCHAR(255),
    uploaded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by             VARCHAR(255),
    reviewed_at             TIMESTAMPTZ,
    metadata                JSONB
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_docs_application ON varapi.provider_council_documents(application_id);

-- Council-specific standing / compliance transitions (separate from provider_status_history)
CREATE TABLE IF NOT EXISTS varapi.provider_council_status_histories (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT      NOT NULL REFERENCES varapi.councils(id),
    previous_status         VARCHAR(50),
    new_status              VARCHAR(50) NOT NULL,
    reason                  TEXT,
    changed_by              VARCHAR(255),
    changed_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    authority_context       VARCHAR(255),
    metadata                JSONB
);

CREATE INDEX IF NOT EXISTS idx_pcouncil_statushist_provider ON varapi.provider_council_status_histories(provider_id);
