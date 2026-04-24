-- Federation pod registrations (matches PodRegistrationEntity / tshepo schema)

CREATE TABLE tshepo.pod_registration (
    id                          BIGSERIAL PRIMARY KEY,
    registration_id             UUID         NOT NULL UNIQUE,
    pod_id                      UUID         NOT NULL UNIQUE,
    pod_name                    VARCHAR(255) NOT NULL,
    pod_certificate             TEXT         NOT NULL,
    capabilities                JSONB        NOT NULL,
    region                      VARCHAR(255),
    facility_ids                JSONB,
    granted_data_classes        JSONB        NOT NULL,
    max_offline_hours           INTEGER      NOT NULL,
    merge_authority             BOOLEAN      NOT NULL,
    revocation_priority         VARCHAR(255) NOT NULL,
    heartbeat_interval_seconds  INTEGER      NOT NULL,
    status                      VARCHAR(32)  NOT NULL,
    last_heartbeat_at           TIMESTAMPTZ,
    issued_at                   TIMESTAMPTZ  NOT NULL,
    expires_at                  TIMESTAMPTZ  NOT NULL,
    revoked_at                  TIMESTAMPTZ,
    revoked_by                  VARCHAR(255),
    revocation_reason           TEXT,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_pod_registration_status ON tshepo.pod_registration (status);
CREATE INDEX idx_pod_registration_expires ON tshepo.pod_registration (expires_at);
