-- ============================================================================
-- workforce-governance-service V004 — Multi-regulator facility<->council
-- relationship model (L2 / T4 build).
--
-- Replaces the "single council per tenant" assumption: a facility may be
-- regulated by MANY councils (e.g. MDPCZ for practitioners, a premises
-- licensing authority, a pharmacy council), each with its own relationship
-- type, registration number and status. No hardcoded single regulator.
-- ============================================================================

CREATE TABLE wgv_facility_regulator_relationship (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         VARCHAR(128) NOT NULL,   -- TUSO facility id (opaque ref)
    council_id          VARCHAR(128) NOT NULL,   -- Varapi/registry council id
    council_code        VARCHAR(64) NOT NULL,    -- e.g. MDPCZ, NMCZ, PCZ (NOT hardcoded)
    relationship_type   VARCHAR(64) NOT NULL DEFAULT 'PRIMARY_REGULATOR',
                                                 -- PRIMARY_REGULATOR|SECONDARY_REGULATOR|
                                                 -- LICENSING_AUTHORITY|ACCREDITING_BODY
    registration_number VARCHAR(128),            -- resolvable, never authenticates
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                                                 -- ACTIVE|SUSPENDED|LAPSED|REVOKED|PENDING
    valid_from          DATE,
    valid_to            DATE,
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT uq_wgv_fac_reg_rel UNIQUE (tenant_id, facility_id, council_id, relationship_type)
);

CREATE INDEX idx_wgv_fac_reg_facility ON wgv_facility_regulator_relationship (tenant_id, facility_id);
CREATE INDEX idx_wgv_fac_reg_council ON wgv_facility_regulator_relationship (tenant_id, council_id);
CREATE INDEX idx_wgv_fac_reg_status ON wgv_facility_regulator_relationship (tenant_id, status);
