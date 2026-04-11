-- =============================================================================
-- V007: Health OS Doctrine v1.2 — add provider_id and subject_id to audit tables
-- Aligns with Health OS Access Control Doctrine §11 and Audit Doctrine §12.
-- =============================================================================

ALTER TABLE tshepo.policy_decision_log
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_id  VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_pdl_provider ON tshepo.policy_decision_log (provider_id)
    WHERE provider_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pdl_subject ON tshepo.policy_decision_log (subject_id)
    WHERE subject_id IS NOT NULL;
