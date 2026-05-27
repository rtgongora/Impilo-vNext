-- =============================================
-- Service : pct-service
-- Flyway  : V007__telemedicine_referrals_and_sessions.sql
-- Purpose : Canonical telemedicine referral/session persistence
-- =============================================

CREATE TABLE IF NOT EXISTS pct_referrals (
    referral_id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID            NOT NULL,
    patient_cpid             VARCHAR(128)    NOT NULL,
    encounter_id             VARCHAR(128),
    provider_id              VARCHAR(128),
    facility_id              VARCHAR(128),
    referral_type            VARCHAR(64)     NOT NULL DEFAULT 'SPECIALIST',
    specialty                VARCHAR(128),
    urgency                  VARCHAR(32)     NOT NULL DEFAULT 'ROUTINE',
    reason                   TEXT            NOT NULL,
    clinical_summary         TEXT,
    status                   VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    stage                    INT             NOT NULL DEFAULT 1,
    preferred_mode           VARCHAR(32),
    modality                 VARCHAR(32),
    virtual_mode             VARCHAR(32),
    routing_target           JSONB           DEFAULT '{}',
    attachment_document_ids  JSONB           DEFAULT '[]',
    messages                 JSONB           DEFAULT '[]',
    responses                JSONB           DEFAULT '[]',
    consent_type             VARCHAR(64),
    consent_status           VARCHAR(32),
    consent_reference        VARCHAR(128),
    mvumo_session_id         VARCHAR(128),
    tshepo_decision_id       VARCHAR(128),
    completion_payload       JSONB           DEFAULT '{}',
    submitted_at             TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_patient
    ON pct_referrals (tenant_id, patient_cpid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_facility_status
    ON pct_referrals (tenant_id, facility_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_provider_status
    ON pct_referrals (tenant_id, provider_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS pct_telehealth_sessions (
    session_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID            NOT NULL,
    patient_cpid             VARCHAR(128)    NOT NULL,
    provider_id              VARCHAR(128),
    facility_id              VARCHAR(128),
    referral_id              UUID            REFERENCES pct_referrals(referral_id),
    encounter_id             VARCHAR(128),
    session_type             VARCHAR(32)     NOT NULL DEFAULT 'VIDEO',
    status                   VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULED',
    room_url                 TEXT,
    channel                  VARCHAR(64),
    access_token             VARCHAR(256),
    scheduled_at             TIMESTAMPTZ,
    started_at               TIMESTAMPTZ,
    ended_at                 TIMESTAMPTZ,
    duration_seconds         BIGINT,
    notes                    TEXT,
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pct_telehealth_tenant_patient
    ON pct_telehealth_sessions (tenant_id, patient_cpid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_telehealth_tenant_facility_status
    ON pct_telehealth_sessions (tenant_id, facility_id, status, created_at DESC);
