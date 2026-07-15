-- =====================================================================================
-- MADI V015 — Stamp the canonical trauma_episode_id on the blood order (phase-owner column).
--
-- Trauma pipeline architecture decision #1: MADI keeps its own blood system-of-record rows and
-- carries the shared trauma_episode_id minted by DAIDZAI, so a trauma patient's blood order
-- resolves to the one canonical episode. Additive + nullable — non-trauma blood orders leave it
-- null. Reserved trauma block for MADI is V015–V044 (episode-stamp V015–18).
-- =====================================================================================

ALTER TABLE blood_orders
    ADD COLUMN IF NOT EXISTS trauma_episode_id UUID;

CREATE INDEX IF NOT EXISTS idx_blood_orders_trauma_episode
    ON blood_orders (trauma_episode_id) WHERE trauma_episode_id IS NOT NULL;
