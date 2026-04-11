-- =============================================================================
-- V38: Health Connect–style ingest dedupe (external record IDs per patient).
-- Maps to existing wellness_activities / wellness_vitals_log via BFF service.
-- =============================================================================

CREATE TABLE IF NOT EXISTS wellness_connect_ingest_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) NOT NULL,
    patient_id           VARCHAR(255) NOT NULL,
    external_record_id   VARCHAR(255) NOT NULL,
    record_type          VARCHAR(64) NOT NULL,
    data_origin_platform VARCHAR(64),
    data_origin_package  VARCHAR(255),
    ingested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hc_ingest UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_hc_ingest_patient_time
    ON wellness_connect_ingest_log (tenant_id, patient_id, ingested_at DESC);
