-- Row-level facility import outcomes (one row per record evaluated in a facility_import_run).
-- Persists raw + canonical values, the evaluated outcome/decision, exclusion/duplicate metadata,
-- acceptable-missing flags, and matched/result facility references so the admin UI can show actual
-- rows (not just aggregate counts). Raw values are preserved as JSONB (never lost on canonicalisation).

CREATE TABLE IF NOT EXISTS tuso.facility_import_row (
    id                      BIGSERIAL PRIMARY KEY,
    import_run_id           BIGINT NOT NULL REFERENCES tuso.facility_import_run(id),
    source_label            VARCHAR(128),
    source_file_name        VARCHAR(255),
    source_row              VARCHAR(32),
    correlation_key         VARCHAR(255),

    -- Canonical (imported) values
    province                VARCHAR(128),
    district                VARCHAR(128),
    facility_name           VARCHAR(255),
    facility_code           VARCHAR(64),
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    facility_type           VARCHAR(64),
    ownership               VARCHAR(64),
    location_category       VARCHAR(64),
    facility_level          VARCHAR(64),
    contact                 VARCHAR(128),
    operating_status        VARCHAR(64),
    bed_capacity            INTEGER,
    opening_date            DATE,
    closure_date            DATE,

    -- Raw source values preserved verbatim (never lost during canonicalisation)
    raw_values              JSONB,

    -- Outcome / decision
    outcome                 VARCHAR(48) NOT NULL,
    import_decision         VARCHAR(24),
    exclusion_reason        VARCHAR(128),

    -- Duplicate review metadata
    duplicate_group_key     VARCHAR(255),
    duplicate_type          VARCHAR(24),

    -- Acceptable-missing structured flags + validation
    acceptable_missing      JSONB,
    has_acceptable_missing  BOOLEAN NOT NULL DEFAULT FALSE,
    validation_warnings     JSONB,
    validation_errors       TEXT,

    -- Facility references
    matched_facility_id     BIGINT,
    result_facility_id      BIGINT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_facility_import_row_run
    ON tuso.facility_import_row (import_run_id);
CREATE INDEX IF NOT EXISTS idx_facility_import_row_outcome
    ON tuso.facility_import_row (import_run_id, outcome);
CREATE INDEX IF NOT EXISTS idx_facility_import_row_dup
    ON tuso.facility_import_row (import_run_id, duplicate_type, duplicate_group_key);
CREATE INDEX IF NOT EXISTS idx_facility_import_row_code
    ON tuso.facility_import_row (import_run_id, facility_code);
