-- =====================================================================================
-- Rito — Experience & Reputation :: V007 (RW8)
-- Facility-level reputation aggregate: all PUBLISHED ratings AT a facility (across every
-- provider), per domain. Derived read-model — never a source of truth; fully regenerated
-- from the ratings. Public reads use verified_* only. Rito owns facility reputation too
-- (provider-reputation-doctrine.md); TUSO composes a read-only, source-tagged summary.
-- =====================================================================================

CREATE TABLE rito.rit_facility_reputation_summary (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    facility_id           UUID NOT NULL,
    domain                VARCHAR(32) NOT NULL,
    reporting_period      VARCHAR(16) NOT NULL,
    rating_count          INT NOT NULL DEFAULT 0,
    verified_count        INT NOT NULL DEFAULT 0,
    mean_score            NUMERIC(10,2),
    verified_mean_score   NUMERIC(10,2),
    distribution          JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_recomputed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_facility_reputation UNIQUE (tenant_id, facility_id, domain, reporting_period)
);
CREATE INDEX idx_rit_facility_reputation_facility ON rito.rit_facility_reputation_summary (tenant_id, facility_id);
