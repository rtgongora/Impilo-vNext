-- ============================================================================
-- V036: HPA national facility enrichment — data model.
--
--   Enriches the imported facility estate (V011/V014 master import) and adds
--   credible current HPA institutions not already represented, WITHOUT a
--   parallel registry and WITHOUT mutating imported source truth. Mirrors the
--   V020 enrichment discipline (verbatim source snapshot, field-level
--   provenance, fingerprinted idempotent runs, review queues not auto-merges).
--
--   Doctrine:
--     * Match against live tuso.facility FIRST; never 1,773 + 6,327.
--     * A credible current HPA institution may be created even when incomplete
--       (REGULATOR_LISTED); missing info becomes structured tasks, not a reject.
--     * Every canonical assertion keeps source/record/batch/effective/observation
--       date + confidence + verification + superseded value + reason.
--     * Trust and completeness are MANY independent dimensions, not one status.
--     * Ambiguity goes to a review queue; it never blocks safe enrich/create.
--     * No canonical hard deletes; rollback is by import batch.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Import batch: one row per HPA enrichment run (mirrors facility_import_run
--    / facility_enrichment_run). File checksums stored for auditability.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.hpa_import_batch (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    bundle_id              VARCHAR(96) NOT NULL,          -- TUSO_HPA_VNEXT_IMPORT_2026_07_19
    source_effective_date  DATE NOT NULL,                 -- 2026-07-17 (current HPA snapshot)
    dry_run                BOOLEAN NOT NULL DEFAULT TRUE,
    feed_checksum          VARCHAR(80),                   -- sha256 of the candidate feed
    file_checksums         JSONB,                         -- {path -> sha256} from the bundle manifest

    candidates_total       INTEGER NOT NULL DEFAULT 0,    -- 6,327 expected
    live_tuso_scanned      INTEGER NOT NULL DEFAULT 0,    -- VERIFIED canonical count at run time
    enriched_existing      INTEGER NOT NULL DEFAULT 0,
    created_establishment  INTEGER NOT NULL DEFAULT 0,
    created_physical       INTEGER NOT NULL DEFAULT 0,
    created_unresolved_site INTEGER NOT NULL DEFAULT 0,
    routed_to_review       INTEGER NOT NULL DEFAULT 0,
    quarantined            INTEGER NOT NULL DEFAULT 0,
    unchanged_idempotent   INTEGER NOT NULL DEFAULT 0,    -- second-run no-ops

    counts_by_outcome      JSONB,                         -- decision_outcome -> count
    quality_report         JSONB,                         -- exceptions, quarantine reasons
    metrics                JSONB,                         -- PIC resolved/unresolved, coords accepted/rejected, contacts, tasks
    status                 VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',  -- RUNNING | COMPLETED | FAILED | ROLLED_BACK
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at           TIMESTAMPTZ,
    initiated_by           VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_hib_tenant ON tuso.hpa_import_batch(tenant_id);
CREATE INDEX IF NOT EXISTS idx_hib_bundle ON tuso.hpa_import_batch(bundle_id);

-- ----------------------------------------------------------------------------
-- 2. Immutable candidate staging: one row per current HPA feed record. Raw
--    source is preserved verbatim; the importer reads this, never mutates it.
--    Deterministic source_record_key = "HPA_CURRENT_2026-07-17:<institutionId>".
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.hpa_candidate_staging (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    batch_id               BIGINT NOT NULL REFERENCES tuso.hpa_import_batch(id),
    source_record_key      VARCHAR(96) NOT NULL,
    hpa_institution_id     BIGINT,
    institution_name_raw   TEXT,
    institution_name_normalized TEXT,
    registration_number_raw VARCHAR(64),
    registration_number_normalized VARCHAR(64),
    institution_type       VARCHAR(128),
    institution_subtype    VARCHAR(128),
    designation            VARCHAR(128),
    regulatory_council     VARCHAR(64),
    regulatory_status      VARCHAR(64),
    province               VARCHAR(128),
    current_to_legacy_status VARCHAR(64),                 -- AUTO_EXACT_REG_CORROBORATED | REVIEW_* | CURRENT_ONLY_NO_SQL_MATCH | AMBIGUOUS_*
    raw_record             JSONB NOT NULL,                -- the full feed line, verbatim
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_hcs_batch_key UNIQUE (batch_id, source_record_key)
);
CREATE INDEX IF NOT EXISTS idx_hcs_batch ON tuso.hpa_candidate_staging(batch_id);
CREATE INDEX IF NOT EXISTS idx_hcs_regnum ON tuso.hpa_candidate_staging(tenant_id, registration_number_normalized);
CREATE INDEX IF NOT EXISTS idx_hcs_srk ON tuso.hpa_candidate_staging(source_record_key);

-- ----------------------------------------------------------------------------
-- 3. Match decision: the reconciliation outcome for each candidate, with full
--    evidence, competing candidates, conflicts, reviewer state and audit.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.hpa_match_decision (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    batch_id               BIGINT NOT NULL REFERENCES tuso.hpa_import_batch(id),
    staging_id             BIGINT NOT NULL REFERENCES tuso.hpa_candidate_staging(id),
    source_record_key      VARCHAR(96) NOT NULL,

    -- One of the 12 canonical outcomes.
    decision_outcome       VARCHAR(48) NOT NULL,
        -- CONFIRMED_EXISTING_ENRICH | NEW_REGULATED_ESTABLISHMENT | NEW_PHYSICAL_FACILITY |
        -- NEW_ESTABLISHMENT_SITE_UNRESOLVED | POSSIBLE_EXISTING_REVIEW | POSSIBLE_SHARED_SITE |
        -- POSSIBLE_RENAME | POSSIBLE_RELOCATION | POSSIBLE_SUCCESSOR | IDENTITY_CONFLICT |
        -- INSUFFICIENT_FOR_CANONICALISATION | QUARANTINED_DATA_QUALITY
    confidence             VARCHAR(16) NOT NULL DEFAULT 'LOW',  -- HIGH | MEDIUM | LOW
    matched_facility_id    BIGINT REFERENCES tuso.facility(id), -- set for enrich / created rows
    matched_facility_uuid  UUID,
    match_evidence         JSONB,                         -- signals: code/alias/reg/name-score/province/pic-overlap
    competing_candidates   JSONB,                         -- [{facilityId, matchType, score}]
    conflicts              JSONB,                         -- material identity conflicts
    reason_codes           VARCHAR(255),                  -- comma-separated machine codes

    applied                BOOLEAN NOT NULL DEFAULT FALSE, -- whether a safe outcome was applied (not dry-run)
    requires_review        BOOLEAN NOT NULL DEFAULT FALSE,
    reviewer_status        VARCHAR(32) NOT NULL DEFAULT 'NONE', -- NONE | PENDING | IN_REVIEW | RESOLVED
    reviewer_decision      VARCHAR(48),
    reviewed_by            VARCHAR(255),
    reviewed_at            TIMESTAMPTZ,
    review_history         JSONB NOT NULL DEFAULT '[]'::jsonb,  -- append-only audit
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_hmd_batch_key UNIQUE (batch_id, source_record_key)
);
CREATE INDEX IF NOT EXISTS idx_hmd_batch ON tuso.hpa_match_decision(batch_id);
CREATE INDEX IF NOT EXISTS idx_hmd_outcome ON tuso.hpa_match_decision(tenant_id, decision_outcome);
CREATE INDEX IF NOT EXISTS idx_hmd_review ON tuso.hpa_match_decision(tenant_id, reviewer_status) WHERE requires_review;
CREATE INDEX IF NOT EXISTS idx_hmd_facility ON tuso.hpa_match_decision(matched_facility_id);

-- ----------------------------------------------------------------------------
-- 4. Field-level assertion / provenance: every canonical field value carries
--    where it came from, how sure we are, and what it superseded. Apply source
--    authority PER FIELD; a stronger/verified value is never silently overwritten.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_field_assertion (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES tuso.facility(id),
    field_path             VARCHAR(96) NOT NULL,          -- e.g. contact.telephone, regulatory.status, location.province
    asserted_value         TEXT,
    source_system          VARCHAR(48) NOT NULL,          -- HPA | LEGACY_SQL | LIVE_TUSO | FACILITY_CLAIM | REGULATOR
    source_record_key      VARCHAR(96),
    batch_id               BIGINT REFERENCES tuso.hpa_import_batch(id),
    source_effective_date  DATE,                          -- when the source asserted it
    observation_date       TIMESTAMPTZ NOT NULL DEFAULT now(), -- when we recorded it
    confidence             VARCHAR(16) NOT NULL DEFAULT 'LOW',
    verification_state     VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED', -- UNVERIFIED | REGULATOR_ASSERTED | VERIFIED
    verified_by            VARCHAR(255),
    verified_at            TIMESTAMPTZ,
    public_visibility      VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',   -- PUBLIC | RESTRICTED | INTERNAL
    is_current             BOOLEAN NOT NULL DEFAULT TRUE,
    superseded_value       TEXT,
    supersession_reason    VARCHAR(255),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ffa_facility_field ON tuso.facility_field_assertion(facility_id, field_path) WHERE is_current;
CREATE INDEX IF NOT EXISTS idx_ffa_batch ON tuso.facility_field_assertion(batch_id);
CREATE INDEX IF NOT EXISTS idx_ffa_source ON tuso.facility_field_assertion(tenant_id, source_system);

-- ----------------------------------------------------------------------------
-- 5. Progressive trust states: MANY independent states, not one status. A
--    facility may be REGULATOR_VERIFIED but not OPERATIONALLY_VALIDATED, and
--    publicly searchable while incomplete.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_progressive_state (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES tuso.facility(id),
    state_key              VARCHAR(40) NOT NULL,
        -- REGULATOR_LISTED | REGULATOR_VERIFIED | FACILITY_CLAIMED | PRACTITIONER_RELATIONSHIP_CONFIRMED |
        -- LOCATION_VERIFIED | SERVICES_VERIFIED | OPERATIONALLY_VALIDATED | IMPILO_CONNECTED |
        -- DISPATCH_READY | PUBLICLY_VISIBLE
    achieved               BOOLEAN NOT NULL DEFAULT FALSE,
    source_system          VARCHAR(48),
    evidence_ref           VARCHAR(255),
    achieved_at            TIMESTAMPTZ,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fps_facility_state UNIQUE (facility_id, state_key)
);
CREATE INDEX IF NOT EXISTS idx_fps_facility ON tuso.facility_progressive_state(facility_id);

-- ----------------------------------------------------------------------------
-- 6. Completeness dimensions: separate score per dimension (not one blob).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_completeness_dimension (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES tuso.facility(id),
    dimension              VARCHAR(32) NOT NULL,
        -- identity | regulatory | location | contact | services | workforce |
        -- access | emergency_dispatch | digital_readiness
    status                 VARCHAR(24) NOT NULL DEFAULT 'MISSING',   -- COMPLETE | PARTIAL | MISSING
    completeness_pct       INTEGER,                       -- 0..100, nullable
    missing_indicators     JSONB,                         -- which fields are absent/disputed
    computed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fcd_facility_dim UNIQUE (facility_id, dimension)
);
CREATE INDEX IF NOT EXISTS idx_fcd_facility ON tuso.facility_completeness_dimension(facility_id);

-- ----------------------------------------------------------------------------
-- 7. Structured data-gap + verification tasks: missing/disputed info is
--    ACTIONABLE, with a responsible actor, accepted evidence and history.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_data_gap_task (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES tuso.facility(id),
    batch_id               BIGINT REFERENCES tuso.hpa_import_batch(id),
    task_type              VARCHAR(48) NOT NULL,          -- DATA_GAP | VERIFICATION
    dimension              VARCHAR(32),                   -- completeness dimension the gap belongs to
    required_information   VARCHAR(255) NOT NULL,
    why_required           VARCHAR(500),
    responsible_actor      VARCHAR(48) NOT NULL,          -- FACILITY | REGULATOR | PROVIDER | CITIZEN | STEWARD
    accepted_evidence      VARCHAR(500),
    priority               VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',    -- HIGH | MEDIUM | LOW
    visibility             VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',  -- PUBLIC | RESTRICTED | INTERNAL
    status                 VARCHAR(24) NOT NULL DEFAULT 'OPEN',      -- OPEN | IN_PROGRESS | RESOLVED | DISMISSED
    resolution_history     JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Idempotent re-runs must not duplicate a still-open gap for the same field.
    CONSTRAINT uq_fdgt_open UNIQUE (facility_id, task_type, required_information)
);
CREATE INDEX IF NOT EXISTS idx_fdgt_facility ON tuso.facility_data_gap_task(facility_id);
CREATE INDEX IF NOT EXISTS idx_fdgt_actor ON tuso.facility_data_gap_task(tenant_id, responsible_actor, status);
CREATE INDEX IF NOT EXISTS idx_fdgt_batch ON tuso.facility_data_gap_task(batch_id);
