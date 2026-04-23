-- Biometric governance: declarative rules for enrollment, verification, identification,
-- deduplication assistance, and step-up across subject types and operational contexts.

CREATE TABLE tshepo.biometric_policy_rule (
    id                      BIGSERIAL PRIMARY KEY,
    subject_type            VARCHAR(20)  NOT NULL,
    workflow_type           VARCHAR(64)  NOT NULL,
    context_type            VARCHAR(64)  NOT NULL,
    modality                VARCHAR(30),
    biometric_intent        VARCHAR(32)  NOT NULL,
    policy_outcome          VARCHAR(20)  NOT NULL,
    enrollment_allowed      BOOLEAN      NOT NULL DEFAULT false,
    verification_allowed    BOOLEAN      NOT NULL DEFAULT false,
    identification_allowed  BOOLEAN      NOT NULL DEFAULT false,
    dedup_support_allowed   BOOLEAN      NOT NULL DEFAULT false,
    fallback_allowed        BOOLEAN      NOT NULL DEFAULT true,
    notes                   TEXT,
    active_flag             BOOLEAN      NOT NULL DEFAULT true,
    priority                INT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_biometric_policy_lookup
    ON tshepo.biometric_policy_rule (active_flag, subject_type, workflow_type, context_type, biometric_intent);

COMMENT ON TABLE tshepo.biometric_policy_rule IS
    'Tshepo-governed matrix for biometric capability: outcome plus fine-grained allowed intents. '
    'Use * for workflow_type or context_type to match any incoming value. NULL modality matches any modality.';

-- Baseline rules (tune per jurisdiction). Higher priority wins on ties.
INSERT INTO tshepo.biometric_policy_rule
(subject_type, workflow_type, context_type, modality, biometric_intent, policy_outcome,
 enrollment_allowed, verification_allowed, identification_allowed, dedup_support_allowed, fallback_allowed, notes, priority, active_flag)
VALUES
('CLIENT', 'CLIENT_REGISTRATION', 'FACILITY', NULL, 'ENROLL', 'ALLOWED',
 true, false, false, false, true, 'Client enrollment capture at facility', 100, true),
('CLIENT', 'POINT_OF_CARE', 'FACILITY', NULL, 'VERIFY', 'ALLOWED',
 false, true, false, false, true, '1:1 verification before sensitive in-person care', 100, true),
('CLIENT', 'POINT_OF_CARE', 'COMMUNITY', NULL, 'VERIFY', 'OPTIONAL',
 false, true, false, false, true, 'Field verification when devices and policy support it', 90, true),
('CLIENT', 'POINT_OF_CARE', 'VIRTUAL', NULL, 'VERIFY', 'RESTRICTED',
 false, true, false, false, true, 'Virtual: verification only if strong binding exists; no broad biometric', 80, true),
('CLIENT', '*', 'VIRTUAL', NULL, 'IDENTIFY', 'PROHIBITED',
 false, false, false, false, true, 'Biometric 1:N identification not meaningful / too risky in generic virtual', 200, true),
('CLIENT', 'DEDUPLICATION_REVIEW', 'FACILITY', NULL, 'DEDUP_SUPPORT', 'RESTRICTED',
 false, false, false, true, true, 'Biometric as assist signal only; merge remains governed', 70, true),
('CLIENT', 'PICKUP_RELEASE', 'LOGISTICS', NULL, 'VERIFY', 'OPTIONAL',
 false, true, false, false, true, 'Optional presence-style verification for regulated handoff', 75, true),
('PROVIDER', 'PROVIDER_ONBOARDING', 'FACILITY', NULL, 'ENROLL', 'ALLOWED',
 true, false, false, false, true, 'Provider workforce enrollment at trusted site', 100, true),
('PROVIDER', 'PROVIDER_STEP_UP', 'FACILITY', NULL, 'STEP_UP', 'OPTIONAL',
 false, true, false, false, true, 'Step-up / workstation re-entry', 100, true),
('PROVIDER', 'PROVIDER_PRIVILEGED_ACTION', 'FACILITY', NULL, 'STEP_UP', 'REQUIRED',
 false, true, false, false, false, 'High-trust action: biometric step-up required; no fallback by default', 110, true),
('PROVIDER', 'PROVIDER_IDENTIFICATION', 'FACILITY', NULL, 'IDENTIFY', 'RESTRICTED',
 false, false, true, false, true, '1:N only in narrow controlled workflows', 50, true),
('PROVIDER', '*', 'VIRTUAL', NULL, 'IDENTIFY', 'PROHIBITED',
 false, false, false, false, true, 'No provider biometric fishing in virtual contexts', 200, true);
