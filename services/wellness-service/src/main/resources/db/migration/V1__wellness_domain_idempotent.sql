-- =============================================================================
-- V1: Wellness domain + Health Connect tables (idempotent).
-- Aligns with experience-bff V26 (wellness + wallet subset) + V38–V40.
-- Same database as experience_bff in local dev; Flyway uses separate history table.
-- =============================================================================

CREATE TABLE IF NOT EXISTS wellness_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    activity_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    steps           INTEGER DEFAULT 0,
    calories_burned INTEGER DEFAULT 0,
    active_minutes  INTEGER DEFAULT 0,
    distance_km     NUMERIC(6,2) DEFAULT 0,
    sleep_hours     NUMERIC(4,1),
    sleep_quality   VARCHAR(20),
    water_ml        INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wellness_daily UNIQUE (tenant_id, patient_id, activity_date)
);

CREATE TABLE IF NOT EXISTS wellness_vitals_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    vital_type      VARCHAR(80) NOT NULL,
    value           NUMERIC(10,2) NOT NULL,
    unit            VARCHAR(20) NOT NULL,
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          VARCHAR(50) DEFAULT 'MANUAL',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vitals_patient ON wellness_vitals_log (tenant_id, patient_id, measured_at DESC);

CREATE TABLE IF NOT EXISTS wellness_mood_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    mood_score      INTEGER NOT NULL CHECK (mood_score BETWEEN 1 AND 5),
    energy_level    INTEGER CHECK (energy_level BETWEEN 1 AND 5),
    stress_level    INTEGER CHECK (stress_level BETWEEN 1 AND 5),
    notes           TEXT,
    logged_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mood_patient ON wellness_mood_log (tenant_id, patient_id, logged_at DESC);

CREATE TABLE IF NOT EXISTS wellness_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    challenge_type  VARCHAR(50) NOT NULL DEFAULT 'STEPS',
    target_value    INTEGER NOT NULL,
    target_unit     VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    participant_count INTEGER DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wellness_challenge_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id    UUID NOT NULL REFERENCES wellness_challenges(id),
    patient_id      VARCHAR(255) NOT NULL,
    current_value   INTEGER DEFAULT 0,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_challenge_participant UNIQUE (challenge_id, patient_id)
);

CREATE TABLE IF NOT EXISTS health_wallets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL UNIQUE,
    balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID NOT NULL REFERENCES health_wallets(id),
    transaction_type VARCHAR(20) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    description     TEXT,
    reference       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions ON wallet_transactions (wallet_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wellness_connect_ingest_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) NOT NULL,
    patient_id           VARCHAR(255) NOT NULL,
    external_record_id   VARCHAR(255) NOT NULL,
    record_type          VARCHAR(64) NOT NULL,
    data_origin_platform VARCHAR(64),
    data_origin_package  VARCHAR(255),
    ingested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hc_ingest UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_hc_ingest_patient_time
    ON wellness_connect_ingest_log (tenant_id, patient_id, ingested_at DESC);

ALTER TABLE wellness_connect_ingest_log
    ADD COLUMN IF NOT EXISTS payload JSONB;

CREATE TABLE IF NOT EXISTS wellness_sleep_segments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(255) NOT NULL,
    patient_id            VARCHAR(255) NOT NULL,
    session_external_id   VARCHAR(255) NOT NULL,
    stage                 VARCHAR(32) NOT NULL,
    start_at              TIMESTAMPTZ NOT NULL,
    end_at                TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sleep_seg_session
    ON wellness_sleep_segments (tenant_id, patient_id, session_external_id);

CREATE INDEX IF NOT EXISTS idx_sleep_seg_patient_time
    ON wellness_sleep_segments (tenant_id, patient_id, start_at DESC);

CREATE TABLE IF NOT EXISTS wellness_exercise_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255) NOT NULL,
    patient_id          VARCHAR(255) NOT NULL,
    external_record_id  VARCHAR(255) NOT NULL,
    exercise_type       VARCHAR(128),
    title               TEXT,
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    energy_kcal         NUMERIC(10, 2),
    distance_m          NUMERIC(14, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exercise_ext UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_exercise_patient_time
    ON wellness_exercise_sessions (tenant_id, patient_id, start_at DESC);

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
