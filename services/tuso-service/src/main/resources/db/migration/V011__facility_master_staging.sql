-- Master Health Facility pack staging (2024-07-23 workbook).
-- Staging only — production Tuso facility rows are upserted via FacilityMasterImportService.

CREATE TABLE IF NOT EXISTS tuso.staging_master_health_facilities_20240723 (
    facility_uid            TEXT PRIMARY KEY,
    facility_code           TEXT,
    facility_name           TEXT NOT NULL,
    province                TEXT,
    district                TEXT,
    facility_type           TEXT,
    ownership               TEXT,
    location_context        TEXT,
    service_level           TEXT,
    status                  TEXT,
    bed_capacity            INTEGER,
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    contact_phone_e164      TEXT,
    source_dataset_date     DATE,
    imported_at             TIMESTAMPTZ,
    reconciled_facility_id  BIGINT REFERENCES tuso.facility(id)
);

CREATE INDEX IF NOT EXISTS idx_staging_mhf_code
    ON tuso.staging_master_health_facilities_20240723 (facility_code);

CREATE TABLE IF NOT EXISTS tuso.facility_import_run (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    pack_id             VARCHAR(64) NOT NULL DEFAULT 'master-health-facility-2024-07-23',
    dry_run             BOOLEAN NOT NULL DEFAULT TRUE,
    records_total       INTEGER NOT NULL DEFAULT 0,
    records_created     INTEGER NOT NULL DEFAULT 0,
    records_updated     INTEGER NOT NULL DEFAULT 0,
    records_skipped     INTEGER NOT NULL DEFAULT 0,
    records_failed      INTEGER NOT NULL DEFAULT 0,
    warnings_count      INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    quality_report      JSONB,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    initiated_by        VARCHAR(255)
);
