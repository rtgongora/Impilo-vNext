-- SHR / Butano linkage intent — persisted until sovereign write API is available.

CREATE TABLE IF NOT EXISTS shr_artifact_linkage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL,
    patient_id      VARCHAR(128) NOT NULL,
    artifact_type   VARCHAR(64) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    correlation_id  VARCHAR(128),
    request_id      VARCHAR(128),
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING_SHR_WRITE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shr_artifact_linkage_patient
    ON shr_artifact_linkage (tenant_id, patient_id, created_at DESC);
