-- Public feedback loop routing (operational-owner ROUTING + triage).
-- A citizen-facing feedbackCategory (granular topic, distinct from the clinical case type)
-- is routed to the correct operational owner with a triage priority. Rito stays the system of
-- record for the case; operational_owner is metadata that drives the routed outbox event so the
-- owning team's governed correction / support queue can consume it.
-- See PublicFeedbackRoutingPolicy for the category -> {caseType, operationalOwner, triagePriority} table.
ALTER TABLE rito.rit_case
    ADD COLUMN IF NOT EXISTS feedback_category  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS operational_owner  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS triage_priority    VARCHAR(16);

-- The owning team polls its queue by operational_owner within a tenant.
CREATE INDEX IF NOT EXISTS idx_rit_case_operational_owner
    ON rito.rit_case (tenant_id, operational_owner)
    WHERE operational_owner IS NOT NULL;
