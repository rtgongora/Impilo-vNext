-- =============================================================================
-- khuluma-service V006 — Duty/on-call presence depth (Phase 5 / W7, G-KH-04)
--
-- Vashandi (workforce) presence is more than online/away: a worker can be ON_DUTY or
-- ON_CALL. duty_status is orthogonal to the availability status and drives the on-call
-- roster (who can be paged for an escalation).
-- =============================================================================

ALTER TABLE khuluma_presence
    ADD COLUMN duty_status VARCHAR(16) NOT NULL DEFAULT 'OFF_DUTY'; -- OFF_DUTY | ON_DUTY | ON_CALL

-- On-call roster lookup: the ON_CALL workers for a tenant.
CREATE INDEX idx_khuluma_presence_duty ON khuluma_presence (tenant_id, duty_status);
