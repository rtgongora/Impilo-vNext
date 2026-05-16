CREATE TABLE IF NOT EXISTS wellness_connected_sources (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  VARCHAR(255) NOT NULL,
    patient_id                 VARCHAR(255) NOT NULL,
    source_type                VARCHAR(64) NOT NULL,
    source_name                VARCHAR(255) NOT NULL,
    source_device_id           VARCHAR(255),
    source_app_id              VARCHAR(255),
    source_priority            INTEGER NOT NULL DEFAULT 100,
    status                     VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    provider_access_allowed    BOOLEAN NOT NULL DEFAULT false,
    clinical_writeback_allowed BOOLEAN NOT NULL DEFAULT false,
    consent_status             VARCHAR(32) NOT NULL DEFAULT 'GRANTED',
    sharing_scope              VARCHAR(64) NOT NULL DEFAULT 'PERSONAL_ONLY',
    category_permissions       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_sources_patient
    ON wellness_connected_sources (tenant_id, patient_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS wellness_source_access_audit (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    patient_id       VARCHAR(255) NOT NULL,
    source_id        UUID REFERENCES wellness_connected_sources(id),
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(64),
    purpose_of_use   VARCHAR(64),
    action           VARCHAR(128) NOT NULL,
    correlation_id   VARCHAR(255),
    request_id       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_source_audit_patient
    ON wellness_source_access_audit (tenant_id, patient_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wellness_remote_alerts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(255) NOT NULL,
    patient_id            VARCHAR(255) NOT NULL,
    source_id             UUID REFERENCES wellness_connected_sources(id),
    category              VARCHAR(64) NOT NULL,
    vital_type            VARCHAR(80),
    measured_at           TIMESTAMPTZ,
    observed_value        NUMERIC(12,4),
    threshold_min         NUMERIC(12,4),
    threshold_max         NUMERIC(12,4),
    unit                  VARCHAR(32),
    status                VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    severity              VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    escalation_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_notes          TEXT,
    reviewed_by_provider  VARCHAR(255),
    reviewed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_remote_alerts_patient
    ON wellness_remote_alerts (tenant_id, patient_id, status, created_at DESC);
