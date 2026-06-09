-- Perioperative procedure episode pipeline (booking → preop → theatre → PACU → complete).

CREATE TABLE inpatient.procedure_episode (
    episode_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    subject_cpid        VARCHAR(64) NOT NULL,
    encounter_id        UUID,
    admission_ref       UUID,
    booking_id          VARCHAR(64),
    theatre_id          UUID,
    procedure_name      VARCHAR(255) NOT NULL,
    procedure_code      VARCHAR(64),
    scheduled_at        TIMESTAMPTZ,
    surgeon_id          VARCHAR(128),
    anaesthetist_id     VARCHAR(128),
    status              VARCHAR(32) NOT NULL DEFAULT 'BOOKED',
    consent_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_procedure_episode_patient ON inpatient.procedure_episode (tenant_id, subject_cpid, scheduled_at DESC);
CREATE INDEX idx_procedure_episode_status ON inpatient.procedure_episode (tenant_id, status);

CREATE TABLE inpatient.procedure_preop_assessment (
    assessment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    assessment_type     VARCHAR(32) NOT NULL,
    asa_class           VARCHAR(8),
    airway_assessment   TEXT,
    fasting_verified    BOOLEAN,
    allergies_reviewed  BOOLEAN,
    nursing_notes       TEXT,
    anaesthetist_notes  TEXT,
    cleared_for_theatre BOOLEAN NOT NULL DEFAULT FALSE,
    assessed_by         VARCHAR(128),
    assessed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_procedure_preop_type ON inpatient.procedure_preop_assessment (episode_id, assessment_type);

CREATE TABLE inpatient.procedure_checklist_item (
    item_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    phase               VARCHAR(32) NOT NULL,
    item_code           VARCHAR(64) NOT NULL,
    item_label          VARCHAR(255) NOT NULL,
    completed           BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by        VARCHAR(128),
    completed_at        TIMESTAMPTZ,
    notes               TEXT
);

CREATE INDEX idx_procedure_checklist_episode ON inpatient.procedure_checklist_item (episode_id, phase);

CREATE TABLE inpatient.procedure_intraop_event (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    event_type          VARCHAR(32) NOT NULL,
    description         TEXT NOT NULL,
    vitals_json         TEXT,
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_intraop_episode ON inpatient.procedure_intraop_event (episode_id, recorded_at);

CREATE TABLE inpatient.procedure_postop_record (
    postop_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL UNIQUE REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    pacu_arrival_at     TIMESTAMPTZ,
    aldrete_score       INTEGER,
    pain_score          INTEGER,
    complications       TEXT,
    disposition         VARCHAR(64),
    postop_orders_json  TEXT,
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
