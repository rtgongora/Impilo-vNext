-- Wave 4 §12 — PACU (recovery) depth.
-- The perioperative case does NOT end when surgery is COMPLETED: the PACU stay is its own tracked
-- phase with a time-series observation set + an Aldrete-based discharge-readiness gate. The patient
-- must not "disappear" from the case when the knife-work finishes.
--
-- ONE case SoR remains inpatient.procedure_episode. This migration adds:
--   (a) a time-series PACU observation table (LINK/projection keyed by episode_id), and
--   (b) discharge-decision + escalation + continuity columns on the existing shallow
--       procedure_postop_record (which held only a single Aldrete/pain snapshot).

-- (a) Time-series PACU observation set ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inpatient.procedure_pacu_observation (
    observation_id            UUID            PRIMARY KEY,
    tenant_id                 UUID            NOT NULL,
    episode_id                UUID            NOT NULL
        REFERENCES inpatient.procedure_episode (episode_id) ON DELETE CASCADE,
    seq                       INT             NOT NULL,
    observed_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    -- Airway / breathing
    airway                    VARCHAR(64),
    breathing                 VARCHAR(64),
    spo2                      INT,
    -- Haemodynamics
    systolic_bp               INT,
    diastolic_bp              INT,
    heart_rate                INT,
    -- Neuro / comfort
    consciousness             VARCHAR(64),
    pain_score                INT,
    nausea_vomiting           VARCHAR(64),
    -- Surgical site / thermoregulation
    bleeding_wound            VARCHAR(64),
    temperature_c             NUMERIC(4,1),
    -- Fluids / output
    urine_output_ml           INT,
    fluids                    VARCHAR(256),
    drains_devices            VARCHAR(256),
    medications               TEXT,
    -- Scoring
    aldrete_score             INT,
    discharge_readiness_band  VARCHAR(16),
    notes                     TEXT,
    recorded_by               VARCHAR(128),
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_pacu_obs_episode_seq UNIQUE (tenant_id, episode_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_pacu_obs_episode
    ON inpatient.procedure_pacu_observation (episode_id, seq);

-- (b) Discharge-decision + escalation + inpatient-continuity columns on the postop record ---------
-- destination: the PACU discharge destination (WARD | ICU | HDU | DISCHARGE | TRANSFER | DEATH).
-- Kept wide enough to hold any status string (Wave 3 lesson: a VARCHAR(16) can't hold
-- "OWNER_UNAVAILABLE" — real Postgres 500s where H2 ddl-auto silently widens).
ALTER TABLE inpatient.procedure_postop_record
    ADD COLUMN IF NOT EXISTS destination                VARCHAR(24),
    ADD COLUMN IF NOT EXISTS unplanned_icu              BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS return_to_theatre          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS discharge_readiness_band   VARCHAR(16),
    ADD COLUMN IF NOT EXISTS discharge_readiness_score  INT,
    ADD COLUMN IF NOT EXISTS nhume_delivery_id          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS linked_admission_ref       UUID,
    ADD COLUMN IF NOT EXISTS escalated_to               VARCHAR(32),
    ADD COLUMN IF NOT EXISTS escalation_ref             VARCHAR(64),
    ADD COLUMN IF NOT EXISTS escalated_at               TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS discharge_decided_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS discharge_decided_by       VARCHAR(128);
