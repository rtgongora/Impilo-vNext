-- WS#6 Inpatient Suite: discharge summary (med reconciliation + discharge meds + follow-up) gated on
-- discharge-clearance completion, mapped toward a Butano/FHIR Composition. inpatient owns the native
-- rich summary; the FHIR Composition projection is emitted to Butano (SHR), never duplicated here.

CREATE TABLE inpatient.discharge_summary (
    summary_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    facility_id      UUID,
    encounter_id     UUID        NOT NULL,
    admission_ref    UUID,
    subject_cpid     VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | FINALISED
    discharge_diagnosis      TEXT,
    discharge_disposition    VARCHAR(40),                     -- HOME | TRANSFER | AGAINST_ADVICE | DECEASED
    hospital_course          TEXT,
    medication_reconciliation_json TEXT,                      -- [{drug,action:CONTINUE|STOP|NEW|CHANGE,...}]
    discharge_medications_json     TEXT,                      -- [{drug,dose,route,frequency,duration}]
    follow_up_json           TEXT,                            -- [{type,when,with,location}]
    referrals_json           TEXT,
    fhir_composition_json    TEXT,                            -- Composition projection emitted toward Butano
    finalised_by     VARCHAR(128),
    finalised_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_discharge_summary_encounter
    ON inpatient.discharge_summary (tenant_id, encounter_id);
CREATE INDEX idx_discharge_summary_patient
    ON inpatient.discharge_summary (tenant_id, subject_cpid, created_at DESC);
