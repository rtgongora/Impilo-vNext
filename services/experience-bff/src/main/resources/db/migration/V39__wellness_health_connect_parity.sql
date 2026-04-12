-- =============================================================================
-- V39: Health Connect parity — audit payload, sleep stages, exercise sessions
-- =============================================================================

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
