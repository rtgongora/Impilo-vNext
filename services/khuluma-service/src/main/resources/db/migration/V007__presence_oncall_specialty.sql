-- =============================================================================
-- khuluma-service V007 — On-call specialty (W2 telemedicine ON_CALL routing)
--
-- ON_CALL teleconsult routing needs a specialty-scoped roster: "who is on call
-- for CARDIOLOGY right now?". specialty is optional metadata on the presence
-- row — a worker without a specialty is still on the tenant-wide roster but is
-- excluded from specialty-filtered lookups.
-- =============================================================================

ALTER TABLE khuluma_presence
    ADD COLUMN specialty VARCHAR(128); -- optional clinical specialty key (e.g. CARDIOLOGY)

-- Specialty-scoped on-call roster lookup.
CREATE INDEX idx_khuluma_presence_duty_specialty
    ON khuluma_presence (tenant_id, duty_status, specialty);
