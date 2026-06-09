-- Discharge clearance workflow and resuscitation phase tracking.

CREATE TABLE inpatient.discharge_clearance (
    clearance_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64),
    encounter_id    UUID NOT NULL,
    admission_ref   UUID,
    clearance_type  VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cleared_by      VARCHAR(128),
    cleared_at      TIMESTAMPTZ,
    waived          BOOLEAN NOT NULL DEFAULT FALSE,
    waived_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_discharge_clearance_encounter_type
    ON inpatient.discharge_clearance (tenant_id, encounter_id, clearance_type);

CREATE INDEX idx_discharge_clearance_encounter
    ON inpatient.discharge_clearance (tenant_id, encounter_id);

CREATE TABLE inpatient.resuscitation_phase (
    phase_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES inpatient.emergency_activation(activation_id) ON DELETE CASCADE,
    phase_type      VARCHAR(50) NOT NULL,
    rhythm          VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    notes           TEXT
);

CREATE INDEX idx_resus_phase_activation
    ON inpatient.resuscitation_phase (activation_id, started_at DESC);
