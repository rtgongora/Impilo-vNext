-- ============================================================================
-- V021 — Rito (Quality, Safety & Client Voice) policy rules (SYS-1 enforcement)
-- Enforces docs/design/rito/policy-spec.md at the ext_authz gateway. Before this,
-- the /internal/v1/rito/** routes had no matching policy_rule (PDP fail-closed DENY);
-- the rito.* spec was queued, not enforced.
--
-- TAXONOMY: the spec's `rito.*` capability roles are reconciled to canonical realm roles:
--   client.submitter/viewer -> CITIZEN/CAREGIVER · agent.khulumaintake -> SUPPORT_AGENT
--   provider.* -> CLINICIAN/DOCTOR/NURSE/MIDWIFE/PHARMACIST · facility.quality_focal/safety_lead
--   -> FACILITY_SAFETY_FOCAL · facility.manager -> FACILITY_ADMIN · supervisor.district -> DISTRICT_PHO
--   · supervisor.province/national -> PUBLIC_HEALTH_OFFICER · regulator.* -> REGULATOR (new realm role)
--   · admin.config -> ADMIN · analytics.viewer -> HEALTH_INFO_OFFICER · system.signal_writer -> SYSTEM
--     (the AI-never-auto-closes invariant is already enforced in-service by guardHumanDecision).
--
-- SCOPE: gateway RBAC only. The §3 sensitive-category identity REDACTION (subject/reporter/provider
-- identity masking unless view_*_identity held) and client.viewer own-subject binding are
-- SUBJECT-relationship checks enforced IN-SERVICE (follow-up, mirroring ClinicalAccessGuard).
-- purpose=NULL wildcard (context-set at runtime); facility_scope=false (facility-restriction is in-service).
--
-- DENY-wins: FACILITY_SAFETY_FOCAL gets broad rito access but is DENIED close/reopen (spec: close is
-- facility.manager + supervisor only). Case CREATE is open to any authenticated actor (feedback/
-- complaint submission is broad by design; sensitive content is protected on READ, not on create).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Case create: any authenticated actor may open a case (resource_type `cases` = POST /rito/cases) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-case-create-any',
 'Any authenticated actor: open a Rito case (complaint/compliment/suggestion/safety concern).',
 NULL, NULL, 'cases', 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/cases"}', true),
-- ── Client own-read (subject-bound in-service) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-case-read-citizen',
 'CITIZEN: track own case + timeline (own-subject bound in-service).',
 NULL, 'CITIZEN', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-case-read-caregiver',
 'CAREGIVER: track dependant case (own-subject bound in-service).',
 NULL, 'CAREGIVER', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/cases"}', true),
-- ── Khuluma intake agent: open + annotate cases on a client''s behalf ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-agent-khuluma-intake',
 'SUPPORT_AGENT: open and annotate cases (messages/parties/links) from a conversation.',
 NULL, 'SUPPORT_AGENT', NULL, 'POST', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/cases"}', true),
-- ── Quality ops: facility manager + supervisors + admin = full Rito read/write ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-quality-ops-facility-manager',
 'FACILITY_ADMIN (facility manager): full quality ops — triage/assign/close/audits/CAPA/QI/config.',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-quality-ops-supervisor-district',
 'DISTRICT_PHO: above-site district quality ops.',
 NULL, 'DISTRICT_PHO', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-quality-ops-supervisor-pho',
 'PUBLIC_HEALTH_OFFICER: above-site province/national quality ops.',
 NULL, 'PUBLIC_HEALTH_OFFICER', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-quality-ops-admin',
 'ADMIN: quality administrator — tools/surveys/SLA/escalation config + full ops.',
 NULL, 'ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
-- ── Facility safety focal: full ops EXCEPT close/reopen (DENY-wins enforces spec close-restriction) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-safety-focal-ops',
 'FACILITY_SAFETY_FOCAL: triage/assign/investigate/audits/CAPA + view sensitive incidents.',
 NULL, 'FACILITY_SAFETY_FOCAL', NULL, NULL, NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-safety-focal-deny-close',
 'FACILITY_SAFETY_FOCAL: cannot close a case (manager/supervisor only).',
 NULL, 'FACILITY_SAFETY_FOCAL', 'close', 'POST', NULL, false, false, 'DENY', 50, '{"path_contains": "/rito/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-safety-focal-deny-reopen',
 'FACILITY_SAFETY_FOCAL: cannot reopen a case (manager/supervisor only).',
 NULL, 'FACILITY_SAFETY_FOCAL', 'reopen', 'POST', NULL, false, false, 'DENY', 50, '{"path_contains": "/rito/cases"}', true),
-- ── Read-only roles: regulator (statutory categories) + analytics (aggregates) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-read-regulator',
 'REGULATOR: read regulated categories / investigate statutory cases (statute-permitting, in-service).',
 NULL, 'REGULATOR', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'rito-read-analytics',
 'HEALTH_INFO_OFFICER (analytics): read dashboards/aggregates (no PII; enforced in-service).',
 NULL, 'HEALTH_INFO_OFFICER', NULL, 'GET', NULL, false, false, 'ALLOW', 60, '{"path_contains": "/rito/"}', true);
