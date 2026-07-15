-- =====================================================================================
-- PCT V039 — Secondary survey: link to the trauma episode + amend-without-overwrite (W5).
--
-- The primary/secondary trauma survey is linked to the canonical trauma episode, and later
-- surveys can AMEND an earlier one (new injuries found on re-examination) WITHOUT overwriting the
-- original — the amendment is a new revision that references the superseded row, which is retained.
-- Additive + nullable.
-- =====================================================================================

ALTER TABLE ed_trauma_survey
    ADD COLUMN IF NOT EXISTS trauma_episode_id UUID,
    ADD COLUMN IF NOT EXISTS amends_survey_id BIGINT,
    ADD COLUMN IF NOT EXISTS revision INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';  -- ACTIVE / AMENDED

CREATE INDEX IF NOT EXISTS idx_ed_trauma_survey_episode
    ON ed_trauma_survey (trauma_episode_id) WHERE trauma_episode_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ed_trauma_survey_amends
    ON ed_trauma_survey (amends_survey_id) WHERE amends_survey_id IS NOT NULL;
