-- ============================================================================
-- V046: Teleconsult task linkage (TM-B7).
--
-- A telemedicine referral is a case on the pct_referrals spine, NOT a
-- pct_journeys row — so referral-scoped follow-up/execution tasks cannot use
-- journey_id (it FKs pct_journeys). Mirroring the V035 precedent that made
-- pct_queue_items.journey_id nullable + added source_ref for pool items, this
-- makes journey_id explicitly nullable and adds:
--   * referral_id   — the owning teleconsult referral (case link)
--   * source_ref    — originating object id (e.g. the OROS order the task tracks)
--   * blocks_closure — a task that must be resolved before the referral can
--                      COMPLETE (closure precondition; e.g. a critical result
--                      review or an unassigned pending order)
--
-- No FK on referral_id: pct_referrals uses a UUID PK and tasks may reference
-- referrals across the shadow-migration window; the linkage is enforced in the
-- service layer, consistent with the pool-item source_ref pattern.
-- ============================================================================

ALTER TABLE pct_tasks ALTER COLUMN journey_id DROP NOT NULL;

ALTER TABLE pct_tasks ADD COLUMN IF NOT EXISTS referral_id VARCHAR(64);
ALTER TABLE pct_tasks ADD COLUMN IF NOT EXISTS source_ref VARCHAR(128);
ALTER TABLE pct_tasks ADD COLUMN IF NOT EXISTS blocks_closure BOOLEAN NOT NULL DEFAULT false;

-- Task board / closure-precondition lookups by referral.
CREATE INDEX IF NOT EXISTS idx_pct_tasks_referral
    ON pct_tasks (tenant_id, referral_id) WHERE referral_id IS NOT NULL;

-- Fast "open blocking tasks for this referral" check at completion time.
CREATE INDEX IF NOT EXISTS idx_pct_tasks_referral_blocking
    ON pct_tasks (referral_id, status) WHERE referral_id IS NOT NULL AND blocks_closure = true;

CREATE INDEX IF NOT EXISTS idx_pct_tasks_source_ref
    ON pct_tasks (tenant_id, source_ref) WHERE source_ref IS NOT NULL;
