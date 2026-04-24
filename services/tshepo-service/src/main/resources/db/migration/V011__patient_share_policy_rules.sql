-- Patient-mediated external provider collaboration — Tshepo policy matrix.
-- Rules are matched on workflow_action, scope_pattern, actor_pattern, and minimum trust level.

CREATE TABLE tshepo.patient_share_policy_rule (
    id                          BIGSERIAL PRIMARY KEY,
    workflow_action             VARCHAR(64)  NOT NULL,
    scope_pattern               VARCHAR(64)  NOT NULL DEFAULT '*',
    actor_pattern               VARCHAR(50)  NOT NULL DEFAULT '*',
    min_trust_level             VARCHAR(64),
    policy_outcome              VARCHAR(20)  NOT NULL,
    permit_read                 BOOLEAN      NOT NULL DEFAULT false,
    permit_write                BOOLEAN      NOT NULL DEFAULT false,
    permit_temp_provider_id     BOOLEAN      NOT NULL DEFAULT false,
    require_council_registration BOOLEAN     NOT NULL DEFAULT true,
    notes                       TEXT,
    active_flag                 BOOLEAN      NOT NULL DEFAULT true,
    priority                    INT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_share_policy_lookup
    ON tshepo.patient_share_policy_rule (active_flag, workflow_action, scope_pattern, actor_pattern);

COMMENT ON TABLE tshepo.patient_share_policy_rule IS
    'Governed decisions for patient share issuance, external activation, temp provider IDs, and contributions. '
    'Use * for scope_pattern or actor_pattern wildcards. NULL min_trust_level matches any incoming trust.';

INSERT INTO tshepo.patient_share_policy_rule
(workflow_action, scope_pattern, actor_pattern, min_trust_level, policy_outcome,
 permit_read, permit_write, permit_temp_provider_id, require_council_registration, notes, priority, active_flag)
VALUES
('SHARE_CREATE', '*', 'CITIZEN', NULL, 'ALLOWED',
 true, false, false, false, 'Citizen or proxy may request a governed share artifact', 200, true),
('SHARE_CREATE', '*', 'SYSTEM', NULL, 'ALLOWED',
 true, false, false, false, 'Assisted issuance on behalf of patient (staff/system actor)', 190, true),

('EXTERNAL_ACTIVATE', '*', 'EXTERNAL_PROVIDER', 'SELF_ASSERTED', 'ALLOWED',
 true, false, false, true, 'Read-only workspace after self-assertion (weak trust)', 120, true),
('EXTERNAL_ACTIVATE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FORMAT_VALID', 'ALLOWED',
 true, true, true, true, 'Bounded write + temp provider when council number is structurally valid', 140, true),
('EXTERNAL_ACTIVATE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FOUND', 'ALLOWED',
 true, true, true, true, 'Stronger trust when council number matches Varapi import/onboarded data', 160, true),
('EXTERNAL_ACTIVATE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_AND_IDENTITY_MATCH', 'ALLOWED',
 true, true, true, true, 'Council number + identity alignment with trusted record', 170, true),
('EXTERNAL_ACTIVATE', '*', 'EXTERNAL_PROVIDER', 'FULLY_LINKED_OR_ONBOARDED', 'ALLOWED',
 true, true, false, false, 'Linked Varapi profile — temp provider issuance not required', 180, true),

('TEMP_PROVIDER_ISSUE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FORMAT_VALID', 'ALLOWED',
 false, false, true, true, 'Issue bounded temporary Provider ID at format-valid trust', 130, true),
('TEMP_PROVIDER_ISSUE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FOUND', 'ALLOWED',
 false, false, true, true, 'Issue temp ID when council registry match exists', 150, true),

('CONTRIBUTION_WRITE', 'ENTIRE_CLIENT_RECORD', 'EXTERNAL_PROVIDER', 'COUNCIL_AND_IDENTITY_MATCH', 'RESTRICTED',
 true, false, false, true, 'Whole-record scope: deny external writes by default (read-only)', 90, true),
('CONTRIBUTION_WRITE', '*', 'EXTERNAL_PROVIDER', 'SELF_ASSERTED', 'PROHIBITED',
 false, false, false, true, 'No clinical contributions on pure self-assertion', 80, true),
('CONTRIBUTION_WRITE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FORMAT_VALID', 'ALLOWED',
 false, true, false, true, 'Allow bounded contributions when council format valid or higher', 110, true),
('CONTRIBUTION_WRITE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_NUMBER_FOUND', 'ALLOWED',
 false, true, false, true, 'Contributions with registry-backed council match', 115, true),
('CONTRIBUTION_WRITE', '*', 'EXTERNAL_PROVIDER', 'COUNCIL_AND_IDENTITY_MATCH', 'ALLOWED',
 false, true, false, true, 'Contributions with identity-aligned council match', 118, true);
