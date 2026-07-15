-- =====================================================================================
-- PCT V037 — ED pre-arrival projection on the ED visit.
--
-- Trauma pipeline W3: a prehospital ePCR captured by the ambulance crew (owned by DAIDZAI in
-- dai_ems_epcr / dai_ems_epcr_event) must reach the destination ED BEFORE the patient arrives.
-- DAIDZAI pushes a compact snapshot; PCT materialises it as a PRE_ARRIVAL ed_visit keyed by
-- trauma_episode_id (created ahead of physical arrival). On arrival (W4 handover) the same visit
-- transitions to an active ED visit — no duplicate record. Additive + nullable.
-- =====================================================================================

ALTER TABLE ed_visit
    ADD COLUMN IF NOT EXISTS pre_arrival_json JSONB,
    ADD COLUMN IF NOT EXISTS ems_mission_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pre_arrival_at TIMESTAMPTZ;

-- A PRE_ARRIVAL board query is by facility + status; the episode key makes the push idempotent.
CREATE INDEX IF NOT EXISTS idx_ed_visit_pre_arrival
    ON ed_visit (facility_id, status) WHERE status = 'PRE_ARRIVAL';
