-- ============================================================================
-- V027: HPA practitioner-in-charge candidate relationships.
--
--   The HPA snapshot names a practitioner-in-charge for each institution
--   (6,580 rows, 98% with a council registration number). These are imported as
--   CANDIDATE relationships that grant NO authority — never employment, scope,
--   Facility Mode, administrative or operational access. Each candidate is
--   resolved (best-effort) to an existing varapi.provider by registration number;
--   unresolved ones become onboarding candidates for review.
--
--   Materialisation into the authoritative varapi.practitioner_in_charge_
--   assignments happens ONLY when the provider AND the tuso facility both
--   resolve and a human approves — never from this import alone.
-- ============================================================================

CREATE TABLE IF NOT EXISTS varapi.hpa_practitioner_candidate (
    id                           BIGSERIAL PRIMARY KEY,
    tenant_id                    UUID NOT NULL,
    bundle_id                    VARCHAR(96) NOT NULL,
    source_record_key            VARCHAR(120) NOT NULL,
    source_effective_date        DATE,

    hpa_institution_id           BIGINT,                    -- links to tuso facility via HPA_INSTITUTION_ID=HPA-<id>
    facility_registration_number VARCHAR(64),
    practitioner_sequence        INTEGER,
    practitioner_name            VARCHAR(255),
    practitioner_registration_number VARCHAR(64),
    relationship_role            VARCHAR(64) NOT NULL DEFAULT 'PRACTITIONER_IN_CHARGE',

    -- Best-effort resolution against existing varapi providers (by registration number).
    resolved_provider_id         BIGINT REFERENCES varapi.provider(id),
    varapi_resolution_status     VARCHAR(24) NOT NULL DEFAULT 'UNRESOLVED',  -- RESOLVED | UNRESOLVED | AMBIGUOUS

    -- Candidate lifecycle — never authority-bearing.
    approval_state               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    authority_grant              VARCHAR(64) NOT NULL DEFAULT 'NONE',        -- import NEVER grants authority
    onboarding_status            VARCHAR(24) NOT NULL DEFAULT 'OPEN',        -- OPEN | IN_REVIEW | ONBOARDED | DISMISSED

    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_hpc_bundle_key UNIQUE (bundle_id, source_record_key)
);
CREATE INDEX IF NOT EXISTS idx_hpc_tenant_status ON varapi.hpa_practitioner_candidate(tenant_id, varapi_resolution_status);
CREATE INDEX IF NOT EXISTS idx_hpc_institution ON varapi.hpa_practitioner_candidate(hpa_institution_id);
CREATE INDEX IF NOT EXISTS idx_hpc_provider ON varapi.hpa_practitioner_candidate(resolved_provider_id);
CREATE INDEX IF NOT EXISTS idx_hpc_reg ON varapi.hpa_practitioner_candidate(tenant_id, practitioner_registration_number);
