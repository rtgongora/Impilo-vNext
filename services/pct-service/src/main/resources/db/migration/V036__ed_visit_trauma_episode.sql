-- =====================================================================================
-- PCT V036 — Stamp the canonical trauma_episode_id on the ED visit (phase-owner column).
--
-- Trauma pipeline architecture decision #1: PCT keeps its own ED system-of-record rows and
-- carries the shared trauma_episode_id minted by DAIDZAI. An arriving trauma patient's ED visit
-- inherits the episode id from the incident (via the X-Trauma-Episode-ID header); an ED-first
-- walk-in trauma mints the episode against PCT (origin_kind=ED_WALK_IN, origin_key=ed_visit id)
-- and stamps it here. Additive + nullable — non-trauma ED visits leave it null.
-- =====================================================================================

ALTER TABLE ed_visit
    ADD COLUMN IF NOT EXISTS trauma_episode_id UUID;

CREATE INDEX IF NOT EXISTS idx_ed_visit_trauma_episode
    ON ed_visit (trauma_episode_id) WHERE trauma_episode_id IS NOT NULL;
