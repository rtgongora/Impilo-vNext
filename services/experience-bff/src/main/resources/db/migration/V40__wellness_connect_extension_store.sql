-- =============================================================================
-- V40: Health Connect long-tail + sensitive datatypes — structured extension row
--      (canonical ingest still dedupes via wellness_connect_ingest_log).
-- =============================================================================

CREATE TABLE IF NOT EXISTS wellness_connect_extension (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) NOT NULL,
    patient_id           VARCHAR(255) NOT NULL,
    external_record_id   VARCHAR(255) NOT NULL,
    canonical_type       VARCHAR(128) NOT NULL,
    start_at             TIMESTAMPTZ,
    end_at               TIMESTAMPTZ,
    payload              JSONB NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hc_extension UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_hc_extension_patient_time
    ON wellness_connect_extension (tenant_id, patient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_hc_extension_type
    ON wellness_connect_extension (tenant_id, patient_id, canonical_type);
