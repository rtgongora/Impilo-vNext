-- Widen action and resource_type columns in policy_decision_log.
-- deriveAction() produces "METHOD:PATH" which easily exceeds 64 chars
-- for real API paths (e.g. "GET:/internal/v1/profile/visibility").
-- resource_type is also derived from path segments and can be longer.

ALTER TABLE tshepo_authz.policy_decision_log
    ALTER COLUMN action        TYPE VARCHAR(255),
    ALTER COLUMN resource_type TYPE VARCHAR(255);
