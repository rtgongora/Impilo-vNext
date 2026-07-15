-- Wave 4 §14 — Surgery-specialised discharge summary.
-- The generic inpatient discharge summary (V017/V019) is encounter-keyed and clinically generic. A
-- surgical discharge additionally carries: procedure summary (from the operative note), wound/drain
-- status, pathology-result follow-up (pending histopath survives discharge), rehab plan, sick-leave,
-- referral-back, patient-facing instructions, and a provider summary. This is an episode-keyed
-- projection of the SAME case SoR (procedure_episode) — not a duplicate discharge record.
CREATE TABLE IF NOT EXISTS inpatient.procedure_discharge (
    discharge_id                UUID            PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    episode_id                  UUID            NOT NULL UNIQUE
        REFERENCES inpatient.procedure_episode (episode_id) ON DELETE CASCADE,
    subject_cpid                VARCHAR(128),
    encounter_ref               UUID,
    final_diagnosis             TEXT,
    procedure_summary           TEXT,
    complications               TEXT,
    wound_status                VARCHAR(160),
    drain_status                VARCHAR(160),
    discharge_medications_json  TEXT,
    follow_up_date              DATE,
    follow_up_service           VARCHAR(160),
    follow_up_booking_ref       VARCHAR(64),
    follow_up_is_teleconsult    BOOLEAN         NOT NULL DEFAULT FALSE,
    warning_signs               TEXT,
    rehab_plan                  TEXT,
    -- Snapshot of pending histopathology at discharge — pathology is tracked AFTER discharge.
    pending_pathology_json      TEXT,
    sick_leave_days             INT,
    supporting_docs_json        TEXT,
    referral_back_facility_ref  VARCHAR(64),
    patient_instructions        TEXT,
    provider_summary            TEXT,
    butano_document_ref         VARCHAR(128),
    status                      VARCHAR(24)     NOT NULL DEFAULT 'DRAFT',
    authored_by                 VARCHAR(128),
    completed_by                VARCHAR(128),
    completed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_procedure_discharge_tenant
    ON inpatient.procedure_discharge (tenant_id, status);
