-- =====================================================================================
-- Inpatient V035 — Stamp the canonical trauma_episode_id on the resuscitation record.
--
-- Trauma pipeline architecture decision #1/#3: the ABCDE resuscitation record is re-keyed onto the
-- shared trauma episode. In W1 this is an additive, nullable column so a trauma patient's resus
-- record resolves to the one DAIDZAI-minted episode; W5 re-keys resus fully onto the episode and
-- adds the ABCDE resuscitation_event time-series. Trauma block for inpatient is V035–V064 (V034
-- procedure_episode.trauma_episode_id is theatre-owned; this is the resuscitation-scoped column).
-- =====================================================================================

ALTER TABLE inpatient.resuscitation_record
    ADD COLUMN IF NOT EXISTS trauma_episode_id UUID;

CREATE INDEX IF NOT EXISTS idx_resuscitation_record_trauma_episode
    ON inpatient.resuscitation_record (trauma_episode_id)
    WHERE trauma_episode_id IS NOT NULL;
