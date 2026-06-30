-- ============================================================================
-- V029 — Theatre & Perioperative Depth policy rules (WS#6 theatre-depth seam)
-- Adds ext_authz ALLOW rules for the NEW theatre endpoints under
-- /internal/v1/theatre/cases/{id}/... served by inpatient-service's theatre module.
-- Mirrors the impilo.theatre OPA policy so the gateway and the OPA bundle agree.
--
-- Next free authz migration: latest was V028 (Daidzai), so this WS#6 theatre seam
-- lands at V029.
--
-- COLLISION SAFETY: the PDP derives resource_type as the last path segment, which
-- collides for generic words (`book`, `triage`, `start`, `cancel`, `sign`, `death`,
-- `readiness`, `note`, `cases`, `safety-events`). Each rule pins `path_contains:
-- "/theatre/"` so a same-segment endpoint elsewhere is NOT granted here.
--
-- Scope: actor_type=PROVIDER, theatre clinical roles, action=POST, facility_scope
-- true, purpose TREATMENT. Senior-gated actions:
--   * note SIGN → SURGEON/CONSULTANT only (surgical scope; NURSE/ANAESTHETIST not granted)
--   * booking/triage → SURGEON/CONSULTANT/CLINICIAN (coordinators)
--   * anaesthesia readiness → ANAESTHETIST/CONSULTANT only
-- Safety reporting + death routing are granted to all theatre roles (a safety event
-- must never be blocked). The in-service guards bind subject/scope/WHO-checklist
-- state and emergency override; these gateway rules make the routes reachable.
-- DENY-wins + first-matching-ALLOW semantics unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── Intake (POST /internal/v1/theatre/cases) — `cases` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-case-intake-surgeon',
 'SURGEON: intake an OROS PROCEDURE order as a theatre case.',
 'PROVIDER', 'SURGEON', 'cases', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-case-intake-coordinator',
 'CLINICIAN: intake a theatre case.',
 'PROVIDER', 'CLINICIAN', 'cases', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

-- ── Triage (POST .../{id}/triage) — `triage` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-triage-surgeon',
 'SURGEON: set theatre case triage priority.',
 'PROVIDER', 'SURGEON', 'triage', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-triage-clinician',
 'CLINICIAN: set theatre case triage priority.',
 'PROVIDER', 'CLINICIAN', 'triage', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Readiness evaluate (POST .../{id}/readiness) — `readiness` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-readiness-surgeon',
 'SURGEON: evaluate theatre booking readiness (owner-routed).',
 'PROVIDER', 'SURGEON', 'readiness', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-readiness-coordinator',
 'CLINICIAN: evaluate theatre booking readiness.',
 'PROVIDER', 'CLINICIAN', 'readiness', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Book (POST .../{id}/book) — `book` segment; pinned; coordinator roles ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-book-surgeon',
 'SURGEON: confirm a theatre booking (fails safely on owner blockers).',
 'PROVIDER', 'SURGEON', 'book', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-book-clinician',
 'CLINICIAN: confirm a theatre booking.',
 'PROVIDER', 'CLINICIAN', 'book', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Anaesthesia readiness (POST .../{id}/anaesthesia/...) — anaesthesia-scope only ──
-- (in-service the anaesthesia clearance is captured via /preop; this gateway rule keeps
--  the anaesthesia surface anaesthetist-gated. NURSE/SURGEON not granted.)
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-anaesthesia-anaesthetist',
 'ANAESTHETIST: complete anaesthesia readiness/clearance for theatre.',
 'PROVIDER', 'ANAESTHETIST', 'anaesthesia', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/"}', true),

-- ── Start (POST .../{id}/start) — `start` segment COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-start-surgeon',
 'SURGEON: start theatre (WHO Sign-In gate + emergency override enforced in-service).',
 'PROVIDER', 'SURGEON', 'start', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-start-clinician',
 'CLINICIAN: start theatre.',
 'PROVIDER', 'CLINICIAN', 'start', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Operative note draft (POST .../{id}/note) — `note` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-note-draft-surgeon',
 'SURGEON: draft the operative note.',
 'PROVIDER', 'SURGEON', 'note', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-note-draft-clinician',
 'CLINICIAN: draft the operative note.',
 'PROVIDER', 'CLINICIAN', 'note', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Operative note SIGN (POST .../{id}/note/sign) — `sign` segment; SURGICAL SCOPE only ──
-- NURSE/ANAESTHETIST intentionally NOT granted (mirrors OPA surgical_signer_role).
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-note-sign-surgeon',
 'SURGEON: sign the operative note (Varapi surgical scope re-checked in-service).',
 'PROVIDER', 'SURGEON', 'sign', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-note-sign-consultant',
 'CONSULTANT: sign the operative note.',
 'PROVIDER', 'CONSULTANT', 'sign', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── PACU disposition (POST .../{id}/pacu/disposition) — `disposition` segment; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-pacu-disposition-surgeon',
 'SURGEON: record PACU disposition (death routes to PCT in-service).',
 'PROVIDER', 'SURGEON', 'disposition', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-pacu-disposition-anaesthetist',
 'ANAESTHETIST: record PACU disposition.',
 'PROVIDER', 'ANAESTHETIST', 'disposition', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Cancel (POST .../{id}/cancel) — `cancel` segment COLLIDES; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-cancel-surgeon',
 'SURGEON: cancel a theatre case (releases owner reservations).',
 'PROVIDER', 'SURGEON', 'cancel', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-cancel-clinician',
 'CLINICIAN: cancel a theatre case.',
 'PROVIDER', 'CLINICIAN', 'cancel', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),

-- ── Safety events (POST .../{id}/safety-events) — `safety-events` segment; ANY theatre role ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-safety-nurse',
 'NURSE: report a theatre safety event (never blocked; routed to Rito/Madi/asset-registry).',
 'PROVIDER', 'NURSE', 'safety-events', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-safety-surgeon',
 'SURGEON: report a theatre safety event.',
 'PROVIDER', 'SURGEON', 'safety-events', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-safety-anaesthetist',
 'ANAESTHETIST: report a theatre safety event.',
 'PROVIDER', 'ANAESTHETIST', 'safety-events', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/theatre/cases/"}', true),

-- ── Death routing (POST .../{id}/death) — `death` segment; ANY theatre role ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-death-surgeon',
 'SURGEON: route a death-in-theatre to the PCT Death & Post-Death Pathway.',
 'PROVIDER', 'SURGEON', 'death', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-death-anaesthetist',
 'ANAESTHETIST: route a death-in-theatre to PCT.',
 'PROVIDER', 'ANAESTHETIST', 'death', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/"}', true);
