-- MADI gap-closure: pre-screening, bedside verification, IoT fridge monitoring
-- Service : madi-service
-- Flyway  : V002__gap_closure.sql

-- Self-service donor pre-screening (privacy-safe; no clinical diagnosis)
CREATE TABLE madi.donor_pre_screenings (
    id              BIGSERIAL PRIMARY KEY,
    screening_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    donor_id        UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    answers_json    JSONB NOT NULL DEFAULT '{}',
    outcome         VARCHAR(32) NOT NULL,
    safe_message    TEXT,
    facility_id     UUID,
    jurisdiction    VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_madi_prescreen_donor ON madi.donor_pre_screenings (tenant_id, donor_id, created_at DESC);

-- Bedside patient + unit verification before transfusion
ALTER TABLE madi.transfusion_episodes
    ADD COLUMN IF NOT EXISTS blood_unit_id UUID,
    ADD COLUMN IF NOT EXISTS patient_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS patient_verification_method VARCHAR(32),
    ADD COLUMN IF NOT EXISTS patient_biometric_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS patient_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS patient_verified_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS unit_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS unit_verification_method VARCHAR(32),
    ADD COLUMN IF NOT EXISTS unit_scan_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS unit_verified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS unit_verified_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pre_transfusion_checks_json JSONB DEFAULT '{}';

-- IoT blood-fridge monitoring readiness
ALTER TABLE madi.blood_fridges
    ADD COLUMN IF NOT EXISTS iot_device_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS target_temp_min_c DECIMAL(5,2) DEFAULT 2.0,
    ADD COLUMN IF NOT EXISTS target_temp_max_c DECIMAL(5,2) DEFAULT 6.0,
    ADD COLUMN IF NOT EXISTS last_reading_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS alarm_status VARCHAR(32) DEFAULT 'NORMAL';

CREATE TABLE madi.blood_fridge_readings (
    id              BIGSERIAL PRIMARY KEY,
    reading_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    fridge_id       UUID NOT NULL REFERENCES madi.blood_fridges(fridge_id),
    iot_reading_ref VARCHAR(128),
    temperature_c   DECIMAL(5,2) NOT NULL,
    alarm_status    VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_madi_fridge_readings ON madi.blood_fridge_readings (fridge_id, recorded_at DESC);

-- Offline drive capture sync metadata
CREATE TABLE madi.donation_drive_sync_conflicts (
    id              BIGSERIAL PRIMARY KEY,
    conflict_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    drive_id        UUID NOT NULL,
    local_op_id     VARCHAR(128) NOT NULL,
    server_state    JSONB,
    local_state     JSONB NOT NULL,
    resolution      VARCHAR(32),
    resolved_by     VARCHAR(128),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
