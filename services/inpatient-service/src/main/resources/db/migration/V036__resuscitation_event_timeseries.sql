-- =====================================================================================
-- Inpatient V036 — ABCDE resuscitation time-series (resuscitation_event).
--
-- Trauma pipeline W5 (architecture decision #3): the resuscitation record is re-keyed onto the
-- canonical trauma_episode_id (added in V035) and the hardcoded-vitals / checklist-JSON demo is
-- replaced with a real repeated-entry, multi-channel ABCDE time-series. One row per timed
-- observation on a channel (AIRWAY / BREATHING / CIRCULATION / DISABILITY / EXPOSURE / DRUG /
-- RHYTHM / CPR), so the resuscitation reads back ordered and multi-channel on the episode.
-- resuscitation_* is the trauma lane's exclusive class on the shared inpatient service.
-- =====================================================================================

CREATE TABLE inpatient.resuscitation_event (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    activation_id      UUID NOT NULL REFERENCES inpatient.emergency_activation (activation_id) ON DELETE CASCADE,
    -- Re-key onto the canonical trauma episode (the correlation spine).
    trauma_episode_id  UUID,
    resus_id           UUID,   -- optional link to the resuscitation_record summary
    channel            VARCHAR(24) NOT NULL,   -- AIRWAY/BREATHING/CIRCULATION/DISABILITY/EXPOSURE/DRUG/RHYTHM/CPR
    code               VARCHAR(64),            -- observation code (e.g. SPO2, GCS, ADRENALINE, VF)
    value_text         VARCHAR(255),
    value_numeric      NUMERIC(12,3),
    unit               VARCHAR(32),
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id           VARCHAR(128),
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_resuscitation_event_activation ON inpatient.resuscitation_event (activation_id, recorded_at);
CREATE INDEX idx_resuscitation_event_episode ON inpatient.resuscitation_event (trauma_episode_id, recorded_at)
    WHERE trauma_episode_id IS NOT NULL;
CREATE INDEX idx_resuscitation_event_channel ON inpatient.resuscitation_event (activation_id, channel, recorded_at);
