-- ============================================================================
-- V030 — Patient Encounter Structured Forms policy rules
-- Fine-grained ext_authz ALLOW rules for the pct-service encounter form endpoints
-- (resolver + response lifecycle). Before this, these POST/PATCH endpoints had no
-- matching policy rule → PDP fail-closed (NO_MATCHING_RULES → DENY).
--
-- The PDP derives resource_type as the last path segment, which collides across
-- services (resolve / responses / submit / amend / void / answers / countersign are
-- generic). Each rule is therefore pinned with a `path_contains` condition scoping it
-- to the /v1/forms/ family, mirroring V018. Fine-grained cadre/scope gating is done by
-- the PCT FormScopeEngine + impilo.pct_forms OPA policy; these rules enforce the RBAC
-- dimensions (clinical role + actor type + facility + TREATMENT purpose) so legitimate
-- clinical form writes are not fail-closed. DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Resolve encounter forms (POST /v1/forms/resolve) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-resolve-clinician',
 'CLINICIAN: resolve the structured form set for an encounter.',
 'PROVIDER', 'CLINICIAN', 'resolve', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/resolve"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-resolve-nurse',
 'NURSE: resolve the structured form set for an encounter.',
 'PROVIDER', 'NURSE', 'resolve', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/forms/resolve"}', true),

-- ── Create draft response (POST /v1/forms/responses) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-response-create-clinician',
 'CLINICIAN: create a structured form response draft.',
 'PROVIDER', 'CLINICIAN', 'responses', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-response-create-nurse',
 'NURSE: create a structured form response draft.',
 'PROVIDER', 'NURSE', 'responses', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/forms/responses"}', true),

-- ── Edit answers (PATCH /v1/forms/responses/{id}/answers) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-answers-clinician',
 'CLINICIAN: edit structured form answers.',
 'PROVIDER', 'CLINICIAN', 'answers', 'PATCH', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-answers-nurse',
 'NURSE: edit structured form answers.',
 'PROVIDER', 'NURSE', 'answers', 'PATCH', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/forms/responses"}', true),

-- ── Submit (POST /v1/forms/responses/{id}/submit) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-submit-clinician',
 'CLINICIAN: submit a structured form response.',
 'PROVIDER', 'CLINICIAN', 'submit', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-submit-nurse',
 'NURSE: submit a structured form response.',
 'PROVIDER', 'NURSE', 'submit', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/forms/responses"}', true),

-- ── Countersign (POST /v1/forms/responses/{id}/countersign) — supervisors ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-countersign-clinician',
 'CLINICIAN (supervisor): countersign a structured form response.',
 'PROVIDER', 'CLINICIAN', 'countersign', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true),

-- ── Amend (POST /v1/forms/responses/{id}/amend) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-amend-clinician',
 'CLINICIAN: amend a submitted structured form response.',
 'PROVIDER', 'CLINICIAN', 'amend', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-amend-nurse',
 'NURSE: amend a submitted structured form response.',
 'PROVIDER', 'NURSE', 'amend', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 56, '{"path_contains": "/forms/responses"}', true),

-- ── Void (POST /v1/forms/responses/{id}/void) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'encounter-form-void-clinician',
 'CLINICIAN: void a structured form response.',
 'PROVIDER', 'CLINICIAN', 'void', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 55, '{"path_contains": "/forms/responses"}', true);
