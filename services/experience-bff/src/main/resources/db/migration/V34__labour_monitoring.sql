CREATE TABLE IF NOT EXISTS labour_monitoring_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   VARCHAR(255) NOT NULL,
    patient_id                  UUID NOT NULL,
    encounter_id                UUID,
    phase                       VARCHAR(40) NOT NULL DEFAULT 'ACTIVE_LABOUR',
    recorded_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
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

CREATE INDEX idx_labour_monitoring_patient
    ON labour_monitoring_entries (tenant_id, patient_id, recorded_at DESC);

CREATE INDEX idx_labour_monitoring_encounter
    ON labour_monitoring_entries (tenant_id, encounter_id, recorded_at DESC);
