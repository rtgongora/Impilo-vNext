-- Optional actor-type and assurance band constraints on biometric policy rules.

ALTER TABLE tshepo.biometric_policy_rule
    ADD COLUMN actor_type VARCHAR(50),
    ADD COLUMN min_assurance_level SMALLINT,
    ADD COLUMN max_assurance_level SMALLINT;

COMMENT ON COLUMN tshepo.biometric_policy_rule.actor_type IS
    'When set (or *), must match evaluate request actorType. NULL = any actor type.';
COMMENT ON COLUMN tshepo.biometric_policy_rule.min_assurance_level IS
    'Inclusive minimum LOA numeric (1–4). NULL = no lower bound.';
COMMENT ON COLUMN tshepo.biometric_policy_rule.max_assurance_level IS
    'Inclusive maximum LOA numeric (1–4). NULL = no upper bound.';

-- Delegated card/payload pickup at facility (VITO portal) — biometric verify lane is optional per V009 CLIENT rules;
-- this row tightens actor to facility-facing staff where jurisdiction requires it (tune per deployment).
INSERT INTO tshepo.biometric_policy_rule
(subject_type, workflow_type, context_type, modality, biometric_intent, policy_outcome,
 enrollment_allowed, verification_allowed, identification_allowed, dedup_support_allowed, fallback_allowed,
 actor_type, min_assurance_level, max_assurance_level, notes, priority, active_flag)
VALUES
('CLIENT', 'DELEGATED_PICKUP', 'FACILITY', NULL, 'VERIFY', 'OPTIONAL',
 false, true, false, false, true,
 'STAFF', 2, NULL, 'Delegated pickup redemption: optional biometric verify for staff at LOA2+', 95, true);
