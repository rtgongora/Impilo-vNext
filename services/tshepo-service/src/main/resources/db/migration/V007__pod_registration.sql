-- TSHEPO — Federation: Pod Registration
-- Stores metadata about external pods participating in the federation.

CREATE TABLE tshepo.pod_registration (
    id                          BIGSERIAL PRIMARY KEY,
    registration_id             UUID NOT NULL UNIQUE,
    pod_id                      UUID NOT NULL UNIQUE,
    pod_name                    VARCHAR(255) NOT NULL,
    pod_certificate             TEXT NOT NULL,
    capabilities                JSONB NOT NULL,
    region                      VARCHAR(255),
    facility_ids                JSONB,
    granted_data_classes        JSONB NOT NULL,
    max_offline_hours           INTEGER NOT NULL,
    merge_authority             BOOLEAN NOT NULL,
    revocation_priority         VARCHAR(50) NOT NULL,
    heartbeat_interval_seconds  INTEGER NOT NULL,
    status                      VARCHAR(50) NOT NULL,
    last_heartbeat_at           TIMESTAMPTZ,
    issued_at                   TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    revoked_at                  TIMESTAMPTZ,
    revoked_by                  VARCHAR(255),
    revocation_reason           TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pod_reg_status ON tshepo.pod_registration (status);
CREATE INDEX idx_pod_reg_pod_id ON tshepo.pod_registration (pod_id);
