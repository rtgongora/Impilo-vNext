-- Patient vitals store (clinical observations recorded by clinicians or IoT devices).
-- Surfaced via PCT GET/POST /v1/vitals and consumed by the Experience BFF.

CREATE TABLE pct_vitals (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    patient_id          VARCHAR(128)    NOT NULL,
    encounter_id        VARCHAR(128),
    recorded_by         VARCHAR(255),
    systolic            INTEGER,
    diastolic           INTEGER,
    heart_rate          INTEGER,
    temperature         NUMERIC(5,2),
    respiratory_rate    INTEGER,
    oxygen_saturation   NUMERIC(5,2),
    weight              NUMERIC(6,2),
    height              NUMERIC(6,2),
    pain_score          INTEGER,
    notes               TEXT,
    recorded_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_vitals_tenant_patient ON pct_vitals (tenant_id, patient_id, recorded_at DESC);
CREATE INDEX idx_pct_vitals_encounter ON pct_vitals (tenant_id, encounter_id);
