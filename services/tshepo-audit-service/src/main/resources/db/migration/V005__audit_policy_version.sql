-- =============================================================================
-- TSHEPO Audit Service — V005 — Add policy_version
-- =============================================================================
SET search_path TO tshepo_audit;

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS policy_version VARCHAR(32);
