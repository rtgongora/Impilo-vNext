CREATE TABLE IF NOT EXISTS maternity_partograph_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    labour_phase    VARCHAR(40) NOT NULL DEFAULT 'ACTIVE_LABOUR',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_by      VARCHAR(255),
    closed_at       TIMESTAMPTZ,
    closed_by       VARCHAR(255),
    outcome         VARCHAR(80),
    summary_notes   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_partograph_sessions_patient
    ON maternity_partograph_sessions (tenant_id, patient_id, status, started_at DESC);

CREATE INDEX idx_partograph_sessions_encounter
    ON maternity_partograph_sessions (tenant_id, encounter_id, status, started_at DESC);

CREATE TABLE IF NOT EXISTS maternity_partograph_points (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                  UUID NOT NULL REFERENCES maternity_partograph_sessions(id) ON DELETE CASCADE,
    tenant_id                   VARCHAR(255) NOT NULL,
    patient_id                  UUID NOT NULL,
    encounter_id                UUID,
    observed_at                 TIMESTAMPTZ NOT NULL,
    recorded_by                 VARCHAR(255),
    fetal_heart_rate_bpm        INTEGER,
    contraction_frequency_10min INTEGER,
    contraction_duration_sec    INTEGER,
    cervical_dilation_cm        NUMERIC(4,1),
    fetal_descent_fifths        INTEGER,
    maternal_pulse_bpm          INTEGER,
    systolic_bp                 INTEGER,
    diastolic_bp                INTEGER,
    temperature_c               NUMERIC(4,1),
    liquor                      VARCHAR(40),
    moulding                    VARCHAR(40),
    caput                       VARCHAR(40),
    oxytocin_rate_miu_min       NUMERIC(6,2),
    urine_volume_ml             INTEGER,
    urine_protein               VARCHAR(40),
    urine_acetone               VARCHAR(40),
    maternal_condition          TEXT,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_partograph_points_session
    ON maternity_partograph_points (session_id, observed_at ASC);

CREATE INDEX idx_partograph_points_patient
    ON maternity_partograph_points (tenant_id, patient_id, observed_at DESC);
