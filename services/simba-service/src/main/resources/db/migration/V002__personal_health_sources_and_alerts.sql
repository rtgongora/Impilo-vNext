CREATE TABLE simba.connected_sources (
    id                         BIGSERIAL PRIMARY KEY,
    source_id                  UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id                  UUID NOT NULL,
    person_cpid                VARCHAR(128) NOT NULL,
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

CREATE INDEX idx_simba_sources_person
    ON simba.connected_sources (tenant_id, person_cpid, status, updated_at DESC);

CREATE TABLE simba.source_access_audit (
    id               BIGSERIAL PRIMARY KEY,
    audit_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id        UUID NOT NULL,
    person_cpid      VARCHAR(128) NOT NULL,
    source_id        UUID,
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(64),
    purpose_of_use   VARCHAR(64),
    action           VARCHAR(128) NOT NULL,
    correlation_id   VARCHAR(255),
    request_id       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simba_source_audit_person
    ON simba.source_access_audit (tenant_id, person_cpid, created_at DESC);

CREATE TABLE simba.remote_alerts (
    id                    BIGSERIAL PRIMARY KEY,
    alert_id              UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id             UUID NOT NULL,
    person_cpid           VARCHAR(128) NOT NULL,
    source_id             UUID,
    category              VARCHAR(64) NOT NULL,
    vital_type            VARCHAR(80),
    measured_at           TIMESTAMPTZ,
    observed_value        DECIMAL(12,4),
    threshold_min         DECIMAL(12,4),
    threshold_max         DECIMAL(12,4),
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

CREATE INDEX idx_simba_remote_alerts_person
    ON simba.remote_alerts (tenant_id, person_cpid, status, created_at DESC);
