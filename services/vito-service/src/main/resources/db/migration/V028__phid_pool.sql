CREATE TABLE IF NOT EXISTS vito.phid_pool (
    id              BIGSERIAL PRIMARY KEY,
    facility_id     VARCHAR(100) NOT NULL,
    phid            VARCHAR(10)  NOT NULL UNIQUE,
    health_id       UUID         NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    reserved_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    assigned_at     TIMESTAMPTZ,
    mrs_patient_id  VARCHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_phid_pool_facility_status
    ON vito.phid_pool (facility_id, status);

CREATE INDEX IF NOT EXISTS idx_phid_pool_health_id
    ON vito.phid_pool (health_id);
