-- ============================================================================
-- V020: Facility classification enrichment — binds the imported national
--       facility estate (V011/V014 master import) to the HPA classification
--       catalogue (V018 tuso.facility_classification) WITHOUT creating a
--       parallel taxonomy and WITHOUT mutating the imported source truth.
--
-- Doctrine:
--   * ONE current regulatory profile row per facility; every change appended
--     to an SQL history table (aggregatable, not a JSONB blob).
--   * Verbatim source snapshot preserved on the profile; the facility table
--     itself gains NO new columns (no facility.bed_count) and its legacy
--     loose columns stay untouched.
--   * Classification is conservative: nothing receives a definitive HPA
--     catalogue label without sufficient facts; catalogue gaps surface as
--     exceptions, never rounded to the nearest row.
--   * Units are never auto-created from facility type: candidates are a
--     review queue (facility_unit_candidate), a human confirms into the real
--     tuso.facility_unit via the existing service.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Current regulatory profile: one row per facility.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_regulatory_profile (
    profile_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    facility_id           BIGINT NOT NULL UNIQUE REFERENCES tuso.facility(id),

    -- Verbatim source snapshot (what the import said, never rewritten by review)
    source_facility_type  VARCHAR(128),
    source_ownership      VARCHAR(128),
    source_level          VARCHAR(64),
    source_bed_capacity   VARCHAR(64),          -- raw text, may be 'N/a' / blank

    -- Canonical dimensions (conservative; UNKNOWN until proven)
    canonical_type        VARCHAR(64),           -- HOSPITAL | CLINIC | RURAL_HEALTH_CENTRE | ... | NULL when unknown
    ownership_authority   VARCHAR(64),           -- normalised ownership grouping
    hpa_route             VARCHAR(64),           -- PUBLIC_MISSION_LA | PRIVATE | AMBIGUOUS | MISSING_REQUIRED_FACTS
    care_setting          VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',   -- INPATIENT | OUTPATIENT_ONLY | UNKNOWN
    mobility              VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',   -- FIXED | MOBILE | UNKNOWN
    specialist_scope      VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',   -- GENERAL | SPECIALIST | UNKNOWN

    -- Bed facts: claimed (promoted from import metadata, never human-editable)
    -- vs confirmed (supplied through the reconciliation work queue).
    bed_count_claimed     INTEGER,
    bed_count_confirmed   INTEGER,
    bed_band              VARCHAR(32),           -- UP_TO_50 | BEDS_51_100 | BEDS_101_PLUS
    bed_band_basis        VARCHAR(32),           -- CLAIMED | CONFIRMED

    -- HPA outcome
    hpa_class             VARCHAR(32) NOT NULL DEFAULT 'NOT_YET_DETERMINED',
                                                 -- A | B | C | NOT_YET_DETERMINED | NOT_APPLICABLE
    hpa_class_justification TEXT,                -- REQUIRED when hpa_class = NOT_APPLICABLE
    hpa_classification_id UUID REFERENCES tuso.facility_classification(classification_id),
    hpa_classification_label VARCHAR(255),

    classification_status VARCHAR(48) NOT NULL DEFAULT 'MISSING_REQUIRED_FACTS',
        -- EXACT_SOURCE_MATCH | DETERMINISTIC_MULTI_FIELD_MATCH |
        -- SUGGESTED_HIGH_CONFIDENCE | AMBIGUOUS | MISSING_REQUIRED_FACTS |
        -- UNMAPPED | HUMAN_CONFIRMED | REJECTED_OR_CORRECTED

    dimension_statuses    JSONB,                 -- per-dimension determinism map
    suggestion            JSONB,                 -- candidate labels + rationale (never auto-applied)
    required_facts        JSONB,                 -- what a human must supply to progress
    source_fields_used    JSONB,                 -- provenance: exact fields the rules consumed

    mapping_rule_code     VARCHAR(96),
    mapping_rule_version  INTEGER,
    evaluation_fingerprint VARCHAR(128),         -- rule code+version+source-facts hash → idempotent re-runs

    units_review_status   VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
                                                 -- NOT_REQUIRED | REQUIRED | COMPLETED
    confirmed_by          VARCHAR(255),
    confirmed_at          TIMESTAMPTZ,
    reclassification_review_required BOOLEAN NOT NULL DEFAULT FALSE,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_frp_tenant ON tuso.facility_regulatory_profile(tenant_id);
CREATE INDEX IF NOT EXISTS idx_frp_status ON tuso.facility_regulatory_profile(classification_status);
CREATE INDEX IF NOT EXISTS idx_frp_units_review ON tuso.facility_regulatory_profile(units_review_status);
CREATE INDEX IF NOT EXISTS idx_frp_class ON tuso.facility_regulatory_profile(hpa_class);

-- ----------------------------------------------------------------------------
-- 2. Append-only history: every profile change, aggregatable by SQL.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_regulatory_profile_history (
    history_id     BIGSERIAL PRIMARY KEY,
    profile_id     UUID NOT NULL,
    facility_id    BIGINT NOT NULL,
    tenant_id      UUID NOT NULL,
    change_kind    VARCHAR(48) NOT NULL,
        -- BACKFILL_CREATED | BACKFILL_UPDATED | BACKFILL_PRESERVED_HUMAN |
        -- FACT_SUPPLIED | HUMAN_CONFIRMED | HUMAN_CORRECTED | HUMAN_REJECTED |
        -- BULK_CONFIRMED | UNIT_REVIEW_COMPLETED
    prev_snapshot  JSONB,
    new_snapshot   JSONB,
    actor          VARCHAR(255),
    run_id         BIGINT,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_frph_facility ON tuso.facility_regulatory_profile_history(facility_id);
CREATE INDEX IF NOT EXISTS idx_frph_kind ON tuso.facility_regulatory_profile_history(change_kind);
CREATE INDEX IF NOT EXISTS idx_frph_run ON tuso.facility_regulatory_profile_history(run_id);

-- ----------------------------------------------------------------------------
-- 3. Enrichment run audit (mirrors tuso.facility_import_run, V011).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_enrichment_run (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    dry_run             BOOLEAN NOT NULL DEFAULT TRUE,
    records_total       INTEGER NOT NULL DEFAULT 0,   -- VERIFIED estate count, never assumed
    records_evaluated   INTEGER NOT NULL DEFAULT 0,
    records_created     INTEGER NOT NULL DEFAULT 0,
    records_updated     INTEGER NOT NULL DEFAULT 0,
    records_unchanged   INTEGER NOT NULL DEFAULT 0,   -- fingerprint no-op
    records_preserved_human INTEGER NOT NULL DEFAULT 0,
    records_failed      INTEGER NOT NULL DEFAULT 0,
    counts_by_status    JSONB,                        -- classification_status → count
    estate_audit        JSONB,                        -- verified totals by type/ownership at run time
    exception_report    JSONB,                        -- CATALOGUE_GAP_*, BLANK_FACILITY_TYPE, conflicts, bad bed values
    status              VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    mapping_rule_code   VARCHAR(96),
    mapping_rule_version INTEGER,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    initiated_by        VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_fer_tenant ON tuso.facility_enrichment_run(tenant_id);

-- ----------------------------------------------------------------------------
-- 4. Versioned mapping rule persistence (InspectionContentSeeder discipline:
--    UNIQUE(code,version) + checksum immutability).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_classification_mapping_rule (
    rule_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(96) NOT NULL,
    version           INTEGER NOT NULL DEFAULT 1,
    title             VARCHAR(255) NOT NULL,
    rule_json         JSONB NOT NULL,
    content_checksum  VARCHAR(96) NOT NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | SOURCE_RECONCILED | VALIDATED_CURRENT_SOURCE | ACTIVE | RETIRED
    source_doc        VARCHAR(64),
    approved_by       VARCHAR(255),
    approved_at       TIMESTAMPTZ,
    effective_from    DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fcmr_code_version UNIQUE (code, version)
);
CREATE INDEX IF NOT EXISTS idx_fcmr_status ON tuso.facility_classification_mapping_rule(status);

-- ----------------------------------------------------------------------------
-- 5. Unit candidates: a review queue, DISTINCT from tuso.facility_unit.
--    Units are NEVER auto-created; origin is never parent-type inference.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_unit_candidate (
    candidate_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    facility_id       BIGINT NOT NULL REFERENCES tuso.facility(id),
    suggested_unit_type VARCHAR(64) NOT NULL,
    origin            VARCHAR(32) NOT NULL,      -- HUMAN_PROPOSED | SERVICE_EVIDENCE (never PARENT_TYPE_INFERENCE)
    service_presence  VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
                                                 -- UNKNOWN | EVIDENCED | CONFIRMED_ABSENT
    status            VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
                                                 -- PROPOSED | CONFIRMED | REJECTED
    rationale         TEXT,
    evidence_ref      VARCHAR(512),
    created_unit_id   BIGINT,                    -- set when a human confirm creates the real facility_unit
    proposed_by       VARCHAR(255),
    decided_by        VARCHAR(255),
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fuc_facility ON tuso.facility_unit_candidate(facility_id);
CREATE INDEX IF NOT EXISTS idx_fuc_status ON tuso.facility_unit_candidate(status);
