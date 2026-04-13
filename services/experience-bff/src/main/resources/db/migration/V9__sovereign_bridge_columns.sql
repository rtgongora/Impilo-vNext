-- V9: Add columns to bridge BFF entities with sovereign service references.
-- These columns store the canonical IDs from PCT, OROS, and other sovereign services
-- so the BFF can correlate its read model with the authoritative domain state.

ALTER TABLE encounters ADD COLUMN IF NOT EXISTS pct_journey_id VARCHAR(255);
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS pct_encounter_ref VARCHAR(255);

ALTER TABLE queue_entries ADD COLUMN IF NOT EXISTS pct_journey_id VARCHAR(255);

-- Telemedicine sessions table for web + mobile
CREATE TABLE IF NOT EXISTS telemedicine_sessions (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    encounter_id    UUID,
    patient_id      UUID,
    provider_id     UUID,
    facility_id     UUID,
    session_type    VARCHAR(50) NOT NULL DEFAULT 'VIDEO',
    status          VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    room_url        VARCHAR(500),
    scheduled_at    TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    duration_seconds INTEGER,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_telemedicine_sessions_tenant ON telemedicine_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_telemedicine_sessions_provider ON telemedicine_sessions(tenant_id, provider_id);
CREATE INDEX IF NOT EXISTS idx_telemedicine_sessions_patient ON telemedicine_sessions(tenant_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_telemedicine_sessions_status ON telemedicine_sessions(tenant_id, status);

-- Citizen telehealth sessions table
CREATE TABLE IF NOT EXISTS citizen_telehealth_sessions (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    patient_id      UUID NOT NULL,
    provider_id     UUID,
    provider_name   VARCHAR(255),
    session_type    VARCHAR(50) NOT NULL DEFAULT 'VIDEO',
    status          VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    scheduled_at    TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    room_url        VARCHAR(500),
    notes           TEXT,
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_citizen_telehealth_tenant ON citizen_telehealth_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_citizen_telehealth_patient ON citizen_telehealth_sessions(tenant_id, patient_id);

-- Lab orders bridge column for OROS order ID
ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS oros_order_id VARCHAR(255);

-- Referrals: add columns for cross-facility tracking
ALTER TABLE referrals ADD COLUMN IF NOT EXISTS receiving_facility_id UUID;
ALTER TABLE referrals ADD COLUMN IF NOT EXISTS receiving_facility_name VARCHAR(255);
ALTER TABLE referrals ADD COLUMN IF NOT EXISTS response_notes TEXT;
ALTER TABLE referrals ADD COLUMN IF NOT EXISTS responded_at TIMESTAMPTZ;
ALTER TABLE referrals ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMPTZ;
