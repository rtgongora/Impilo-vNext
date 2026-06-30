-- WS#8 — Death & Post-Death Pathway: Tshepo policy matrix for who may confirm a death, certify a
-- cause of death, release a body, and access medico-legal detail. Mirrors the per-domain policy-rule
-- pattern (biometric V009, patient-share V011). The OPA impilo.death rego enforces these doctrines at
-- the ext_authz hot path; this DB table is the governed, auditable source the policy admin edits and
-- the engine can consult. Payment NEVER appears here — death documentation is never payment-gated.

CREATE TABLE tshepo.death_policy_rule (
    id                       BIGSERIAL PRIMARY KEY,
    workflow_action          VARCHAR(48)  NOT NULL,  -- CONFIRM|CERTIFY|BODY_RELEASE|MEDICO_LEGAL_VIEW|CRVS_SUBMIT|SELF_SERVICE
    role_pattern             VARCHAR(64)  NOT NULL DEFAULT '*',
    requires_licence         BOOLEAN      NOT NULL DEFAULT false,
    requires_no_medico_legal BOOLEAN      NOT NULL DEFAULT false, -- certify denied while referral open
    requires_no_hold         BOOLEAN      NOT NULL DEFAULT false, -- body release denied while hold active
    policy_outcome           VARCHAR(20)  NOT NULL,  -- ALLOWED | DENIED
    notes                    TEXT,
    active_flag              BOOLEAN      NOT NULL DEFAULT true,
    priority                 INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_death_policy_lookup
    ON tshepo.death_policy_rule (active_flag, workflow_action, role_pattern);

COMMENT ON TABLE tshepo.death_policy_rule IS
    'Governed decisions for death confirmation, cause-of-death certification, body release, '
    'medico-legal access, and CRVS submission. Role patterns may use * wildcard. Death documentation '
    'and registration are never gated on payment.';

INSERT INTO tshepo.death_policy_rule
(workflow_action, role_pattern, requires_licence, requires_no_medico_legal, requires_no_hold,
 policy_outcome, notes, priority, active_flag)
VALUES
-- Confirmation — authorised clinical roles only.
('CONFIRM', 'doctor',           false, false, false, 'ALLOWED', 'Medical practitioner may confirm death', 200, true),
('CONFIRM', 'medical_officer',  false, false, false, 'ALLOWED', 'Medical officer may confirm death', 190, true),
('CONFIRM', 'consultant',       false, false, false, 'ALLOWED', 'Consultant may confirm death', 190, true),
('CONFIRM', 'registrar',        false, false, false, 'ALLOWED', 'Registrar may confirm death', 180, true),
('CONFIRM', 'clinical_officer', false, false, false, 'ALLOWED', 'Clinical officer may confirm (per scope)', 170, true),
('CONFIRM', '*',                false, false, false, 'DENIED',  'No other role may confirm a death', 10, true),

-- Certification — eligible certifier role + licence + no open medico-legal referral.
('CERTIFY', 'doctor',          true, true, false, 'ALLOWED', 'Licensed doctor may certify cause of death', 200, true),
('CERTIFY', 'medical_officer', true, true, false, 'ALLOWED', 'Licensed medical officer may certify', 190, true),
('CERTIFY', 'consultant',      true, true, false, 'ALLOWED', 'Licensed consultant may certify', 190, true),
('CERTIFY', 'registrar',       true, true, false, 'ALLOWED', 'Licensed registrar may certify', 180, true),
('CERTIFY', '*',               true, true, false, 'DENIED',  'No other role may certify cause of death', 10, true),

-- Body release — mortuary role + no active legal/coroner/post-mortem hold.
('BODY_RELEASE', 'mortuary_officer', false, false, true, 'ALLOWED', 'Mortuary officer may authorise release when no hold active', 200, true),
('BODY_RELEASE', 'mortuary_manager', false, false, true, 'ALLOWED', 'Mortuary manager may authorise release when no hold active', 200, true),
('BODY_RELEASE', '*',                false, false, true, 'DENIED',  'No other role may release a body', 10, true),

-- Medico-legal / forensic detail — medico-legal roles only (break-glass handled at runtime).
('MEDICO_LEGAL_VIEW', 'medical_examiner',     false, false, false, 'ALLOWED', 'Medical examiner may view medico-legal detail', 200, true),
('MEDICO_LEGAL_VIEW', 'forensic_pathologist', false, false, false, 'ALLOWED', 'Forensic pathologist may view medico-legal detail', 200, true),
('MEDICO_LEGAL_VIEW', 'coroner_liaison',      false, false, false, 'ALLOWED', 'Coroner liaison may view medico-legal detail', 190, true),
('MEDICO_LEGAL_VIEW', '*',                    false, false, false, 'DENIED',  'Medico-legal detail masked from other roles', 10, true),

-- CRVS submission — crvs roles.
('CRVS_SUBMIT', 'registry_clerk',  false, false, false, 'ALLOWED', 'Registry clerk may assemble/submit CRVS package', 190, true),
('CRVS_SUBMIT', 'civil_registrar', false, false, false, 'ALLOWED', 'Civil registrar may submit CRVS package', 200, true),
('CRVS_SUBMIT', 'doctor',          false, false, false, 'ALLOWED', 'Certifying doctor may submit CRVS package', 180, true),
('CRVS_SUBMIT', '*',               false, false, false, 'DENIED',  'No other role may submit CRVS package', 10, true),

-- Self-service — a confirmed-deceased person's ordinary self-service is disabled.
('SELF_SERVICE', 'deceased', false, false, false, 'DENIED', 'Deceased person ordinary self-service access is disabled', 250, true);
