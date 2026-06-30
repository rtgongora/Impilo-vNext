-- ============================================================================
-- V028 — Daidzai Emergency & Disaster Suite (WS#7) policy rules
-- Adds ext_authz rules for daidzai-service (/internal/v1/daidzai/**). These
-- gateway rules make routes reachable; the OPA `impilo.daidzai` policy + the
-- in-service guards bind subject/role/sensitivity. DENY-wins + first-matching
-- ALLOW semantics unchanged. Next free after V027 (inpatient-suite).
--
-- Doctrine encoded here:
--   * SOS intake is life-safety MINIMAL-friction: ANY authenticated actor
--     (citizen/caregiver/bystander/provider/facility) may POST a request.
--     Payment NEVER gates an emergency (no coverage/wallet rule applies).
--   * Citizen/caregiver may GET their OWN request/incident (own-subject bound
--     in-service); they are NOT granted the operational queue/dispatch/command.
--   * Triage/dispatch/mission/handoff/resource = PROVIDER/STAFF responder roles.
--   * Disaster declare/close/site = COMMAND roles only.
--   * Citizens are explicitly DENIED the operational incident/disaster lists.
--
-- COLLISION SAFETY: the PDP derives resource_type from the last path segment,
-- which collides for generic words (`triage`, `dispatch`, `handoff`, `close`,
-- `resources`, `sites`). Every rule pins `path_contains: "/daidzai/"` (and a
-- more specific subpath where needed) so a same-segment endpoint in another
-- service is NOT granted here.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── 1. SOS intake — any authenticated actor (no payment gate, life-safety) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-sos-create-any',
 'Any authenticated actor: raise an emergency SOS request (life-safety; payment never gates).',
 NULL, NULL, 'requests', 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/daidzai/requests"}', true),

-- ── 2. Own-subject read of request/incident status (citizen / caregiver) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-request-read-citizen',
 'CITIZEN: track own SOS request/incident (own-subject bound in-service).',
 NULL, 'CITIZEN', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/daidzai/requests"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-request-read-caregiver',
 'CAREGIVER: track dependant SOS request/incident (own-subject bound in-service).',
 NULL, 'CAREGIVER', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/daidzai/requests"}', true),

-- ── 3. Citizens DENIED operational incident/disaster lists (command board is staff-only) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-incident-list-deny-citizen',
 'CITIZEN: cannot see the operational incident queue / command board.',
 NULL, 'CITIZEN', NULL, NULL, NULL, false, false, 'DENY', 10, '{"path_contains": "/daidzai/incidents"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-disaster-list-deny-citizen',
 'CITIZEN: cannot see the disaster/MCI command board.',
 NULL, 'CITIZEN', NULL, NULL, NULL, false, false, 'DENY', 10, '{"path_contains": "/daidzai/disasters"}', true),

-- ── 4. Triage / escalation — provider/staff responder ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-triage-dispatcher',
 'DISPATCHER: triage an SOS request into an incident.',
 'PROVIDER', 'DISPATCHER', 'triage', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 56, '{"path_contains": "/daidzai/requests"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-incident-create-provider',
 'PROVIDER (responder): escalate directly to an incident.',
 'PROVIDER', NULL, 'incidents', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 58, '{"path_contains": "/daidzai/incidents"}', true),

-- ── 5. Operational read (queue / missions / command board) — responder/dispatcher ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-incident-list-provider',
 'PROVIDER (responder/dispatcher): view the operational incident queue.',
 'PROVIDER', NULL, 'incidents', 'GET', 'TREATMENT',
 false, false, 'ALLOW', 58, '{"path_contains": "/daidzai/incidents"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-disaster-list-provider',
 'PROVIDER (command): view the disaster/MCI command board.',
 'PROVIDER', NULL, 'disasters', 'GET', 'TREATMENT',
 false, false, 'ALLOW', 58, '{"path_contains": "/daidzai/disasters"}', true),

-- ── 6. Dispatch / mission / handoff / resource — responder roles (segments COLLIDE; pinned) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-dispatch-dispatcher',
 'DISPATCHER: request a dispatch mission for an incident (Nhume executes).',
 'PROVIDER', 'DISPATCHER', 'dispatch', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 56, '{"path_contains": "/daidzai/incidents/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-mission-responder',
 'RESPONDER: record a mission status event on an incident.',
 'PROVIDER', 'RESPONDER', 'mission-events', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 56, '{"path_contains": "/daidzai/incidents/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-handoff-responder',
 'RESPONDER: record the clinical handoff link to a PCT encounter.',
 'PROVIDER', 'RESPONDER', 'handoff', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 56, '{"path_contains": "/daidzai/incidents/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-resource-responder',
 'RESPONDER: raise a resource need against an owner (Nhume/Madi/Dura/...).',
 'PROVIDER', 'RESPONDER', 'resources', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 56, '{"path_contains": "/daidzai/incidents/"}', true),

-- ── 7. Disaster / MCI command — COMMAND roles only (declare/close/site; segments COLLIDE; pinned) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-disaster-declare-commander',
 'RESPONSE_COMMANDER: declare a disaster/MCI command incident.',
 'PROVIDER', 'RESPONSE_COMMANDER', 'disasters', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 57, '{"path_contains": "/daidzai/disasters"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-disaster-close-commander',
 'RESPONSE_COMMANDER: close a disaster and route after-action to Rito.',
 'PROVIDER', 'RESPONSE_COMMANDER', 'close', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 57, '{"path_contains": "/daidzai/disasters/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'daidzai-disaster-site-commander',
 'RESPONSE_COMMANDER: manage affected sites on a disaster incident.',
 'PROVIDER', 'RESPONSE_COMMANDER', 'sites', 'POST', 'TREATMENT',
 false, false, 'ALLOW', 57, '{"path_contains": "/daidzai/disasters/"}', true);
