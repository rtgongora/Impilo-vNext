-- Wave 8: Add policy decision evidence columns for consistency class support
ALTER TABLE tshepo.policy_decision_log
    ADD COLUMN IF NOT EXISTS policy_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS reason_codes   TEXT;

COMMENT ON COLUMN tshepo.policy_decision_log.policy_version IS 'Policy version that produced this decision (e.g. v1.1.0)';
COMMENT ON COLUMN tshepo.policy_decision_log.reason_codes   IS 'Comma-separated machine-readable reason codes';
