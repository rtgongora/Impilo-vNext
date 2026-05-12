-- Widen remaining short varchar columns in policy_decision_log that may overflow
-- for real-world integrator values.
--
-- purpose_of_use  (varchar 32) -> TEXT: custom Purpose-Of-Use codes can exceed 32 chars.
-- actor_type      (varchar 32) -> TEXT: actor type strings can exceed 32 chars.
-- verdict         (varchar 16) -> TEXT: future verdict labels may exceed 16 chars.
-- assurance_level (varchar 16) -> TEXT: assurance level strings may exceed 16 chars.

ALTER TABLE tshepo_authz.policy_decision_log
    ALTER COLUMN purpose_of_use  TYPE TEXT,
    ALTER COLUMN actor_type      TYPE TEXT,
    ALTER COLUMN verdict         TYPE TEXT,
    ALTER COLUMN assurance_level TYPE TEXT;
