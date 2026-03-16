-- V8: Add discharge workflow columns to encounters table
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS discharge_type VARCHAR(50);
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS discharge_diagnosis TEXT;
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS treatment_summary TEXT;
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS follow_up_instructions TEXT;
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS medications_at_discharge TEXT;
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS patient_instructions TEXT;
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS discharged_by VARCHAR(255);
ALTER TABLE encounters ADD COLUMN IF NOT EXISTS discharged_at TIMESTAMPTZ;

-- Add consent_preferences table for citizen app
CREATE TABLE IF NOT EXISTS consent_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL,
    patient_id      UUID NOT NULL,
    consent_type    VARCHAR(100) NOT NULL,
    granted         BOOLEAN NOT NULL DEFAULT FALSE,
    description     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, patient_id, consent_type)
);

CREATE INDEX IF NOT EXISTS idx_consent_preferences_patient ON consent_preferences (tenant_id, patient_id);
