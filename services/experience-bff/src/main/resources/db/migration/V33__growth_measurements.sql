-- =============================================================================
-- V33: Growth measurements with WHO under-5 anthropometric support
-- =============================================================================

CREATE TABLE IF NOT EXISTS growth_measurements (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(255) NOT NULL,
    patient_id              UUID NOT NULL,
    encounter_id            UUID,
    measured_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_by             VARCHAR(255),
    weight_kg               NUMERIC(6,3),
    length_cm               NUMERIC(6,3),
    height_cm               NUMERIC(6,3),
    head_circumference_cm   NUMERIC(6,3),
    muac_cm                 NUMERIC(6,3),
    bmi                     NUMERIC(6,3),
    measurement_mode        VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    notes                   TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (
        weight_kg IS NOT NULL OR
        length_cm IS NOT NULL OR
        height_cm IS NOT NULL OR
        head_circumference_cm IS NOT NULL OR
        muac_cm IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_growth_measurements_patient
    ON growth_measurements (tenant_id, patient_id, measured_at DESC);

CREATE INDEX IF NOT EXISTS idx_growth_measurements_encounter
    ON growth_measurements (tenant_id, encounter_id, measured_at DESC);
