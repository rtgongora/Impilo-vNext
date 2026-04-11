-- =============================================================================
-- TSHEPO V008 — Audit Event Doctrine Completeness (Health OS §22)
--
-- Add device_channel and module_id columns to capture all 12 doctrine
-- audit dimensions:
-- 1. person (actor_id) ✓  7. transaction (resource_type+id) ✓
-- 2. role (actor_type) ✓  8. app/module (module_id) ← NEW
-- 3. role-id (provider_id from V007) ✓  9. purpose (purpose_of_use) ✓
-- 4. org (tenant_id) ✓  10. device/channel (device_channel) ← NEW
-- 5. facility (facility_id) ✓  11. time (occurred_at) ✓
-- 6. subject (subject_id from V007) ✓  12. event (entry_hash) ✓
-- =============================================================================

ALTER TABLE tshepo.audit_event
    ADD COLUMN IF NOT EXISTS device_channel VARCHAR(255),
    ADD COLUMN IF NOT EXISTS module_id      VARCHAR(255);

ALTER TABLE tshepo.policy_decision_log
    ADD COLUMN IF NOT EXISTS device_channel VARCHAR(255),
    ADD COLUMN IF NOT EXISTS module_id      VARCHAR(255);
