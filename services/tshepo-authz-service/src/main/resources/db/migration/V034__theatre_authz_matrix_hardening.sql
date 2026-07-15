-- ============================================================================
-- V034 — Theatre trust/scope/safety authz MATRIX completion (Wave 6 §16).
--
-- V029 (perioperative) + V030 (clinical-safety) made most theatre write endpoints
-- REACHABLE for the correct clinical cadres. This migration COMPLETES the matrix by
-- adding ALLOW rules for the remaining key theatre actions §16 enumerates that
-- V029/V030 did not cover:
--   * WHO safety-checklist phase sign  (POST /procedures/episodes/{id}/checklist/{phase})
--   * anaesthesia depth/ASA scores     (POST /procedures/episodes/{id}/anaesthesia/scores)
--   * surgical discharge / disposition (POST /theatre/cases/{id}/discharge[/complete])
--   * unplanned return-to-theatre      (POST /theatre/cases/{id}/return-to-theatre)
--   * emergency-case creation          (POST /theatre/cases/emergency) — break-glass, min_loa
--   * emergency consent-exception      (POST /theatre/cases/{id}/consent/emergency-exception)
--
-- NEGATIVE (deny) matrix — HOW the "incorrect user is denied" is enforced:
--   The PDP evaluates path_contains ONLY on ALLOW rules (a DENY row matches on
--   actor_type/role/action/purpose alone — see PolicyEngine.evaluatePolicies /
--   matchesRule, which does NOT call evaluateConditions on the DENY branch). A
--   path-pinned explicit DENY is therefore UNSAFE: it would fire for that cadre on
--   the whole surface. So — exactly as V029/V030 do — the theatre negatives are
--   enforced by CORRECT-CADRE-ONLY ALLOW: every theatre ALLOW is actor_type=PROVIDER
--   and role-scoped, so an out-of-scope cadre / CITIZEN / PATIENT matches NO ALLOW
--   rule and is denied NO_ALLOW_RULE (a DENY verdict). The full allow-AND-deny matrix
--   across all 10 access dimensions is proven live in TheatreAuthzMatrixTest.
--
-- Next free authz migration: latest active was V033 (workforce intake). This theatre
-- §16 completion lands at V034. (The Wave-6 brief's "next free = V031" note was stale:
-- V031/V032/V033 were merged in by other waves after that note was written.)
--
-- PIN FORM: path_contains is SEGMENT-BOUNDED (the pin must be followed by '/' or end
-- of path). Pins are therefore written WITHOUT a trailing slash (e.g. "/theatre/cases",
-- "/checklist", "/note/sign") so they match "/theatre/cases/{id}/..." correctly. Every
-- generic last-segment (phase names, `discharge`, `complete`, `emergency`, `exception`,
-- `return-to-theatre`, `scores`) is pinned so a same-segment endpoint elsewhere is NOT
-- granted here. DENY-wins + first-matching-ALLOW semantics unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── WHO safety-checklist phase sign (POST /procedures/episodes/{id}/checklist/{phase}) ──
--    Team-signed: scrub NURSE + SURGEON + ANAESTHETIST. {phase} segment is generic → pin
--    "/checklist" (resource_type NULL wildcard so any phase name participates).
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-checklist-sign-surgeon',
 'SURGEON: sign a WHO safety-checklist phase (sign-in / time-out / sign-out).',
 'PROVIDER', 'SURGEON', NULL, 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/checklist"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-checklist-sign-anaesthetist',
 'ANAESTHETIST: sign a WHO safety-checklist phase.',
 'PROVIDER', 'ANAESTHETIST', NULL, 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/checklist"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-checklist-sign-nurse',
 'NURSE: sign a WHO safety-checklist phase (scrub/circulating nurse).',
 'PROVIDER', 'NURSE', NULL, 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/checklist"}', true),

-- ── Anaesthesia scores (POST /procedures/episodes/{id}/anaesthesia/scores) — ANAESTHETIST ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-anaesthesia-scores-anaesthetist',
 'ANAESTHETIST: record anaesthesia depth/ASA scores.',
 'PROVIDER', 'ANAESTHETIST', 'scores', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/anaesthesia/scores"}', true),

-- ── Theatre discharge / disposition (POST /theatre/cases/{id}/discharge[/complete]) ──
--    SURGEON + CLINICIAN. `discharge`/`complete` segments collide → pin "/theatre/cases".
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-discharge-surgeon',
 'SURGEON: author the surgical discharge / disposition.',
 'PROVIDER', 'SURGEON', 'discharge', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-discharge-clinician',
 'CLINICIAN: author the surgical discharge / disposition.',
 'PROVIDER', 'CLINICIAN', 'discharge', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-discharge-complete-surgeon',
 'SURGEON: finalise (complete) the surgical discharge.',
 'PROVIDER', 'SURGEON', 'complete', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-discharge-complete-clinician',
 'CLINICIAN: finalise (complete) the surgical discharge.',
 'PROVIDER', 'CLINICIAN', 'complete', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

-- ── Return-to-theatre (POST /theatre/cases/{id}/return-to-theatre) — SURGEON + CONSULTANT ──
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-return-surgeon',
 'SURGEON: schedule an unplanned return-to-theatre.',
 'PROVIDER', 'SURGEON', 'return-to-theatre', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-return-consultant',
 'CONSULTANT: schedule an unplanned return-to-theatre.',
 'PROVIDER', 'CONSULTANT', 'return-to-theatre', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

-- ══════════════════════════════════════════════════════════════════════════
-- EMERGENCY-OVERRIDE surface — creating an emergency case and recording an emergency
-- consent-exception are life-saving actions granted to the operating cadres with a
-- min_loa=2 floor (the ASSURANCE dimension) and TREATMENT purpose; the in-service
-- guard stamps the audited emergency_override + break-glass reason.
-- ── Emergency case creation (POST /theatre/cases/emergency) — `emergency` segment ──
-- ══════════════════════════════════════════════════════════════════════════
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-emergency-case-surgeon',
 'SURGEON: create an emergency theatre case (audited emergency override).',
 'PROVIDER', 'SURGEON', 'emergency', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases/emergency", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-emergency-case-consultant',
 'CONSULTANT: create an emergency theatre case.',
 'PROVIDER', 'CONSULTANT', 'emergency', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases/emergency", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-emergency-case-anaesthetist',
 'ANAESTHETIST: create an emergency theatre case.',
 'PROVIDER', 'ANAESTHETIST', 'emergency', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/theatre/cases/emergency", "min_loa": 2}', true),

-- ── Emergency consent-exception (POST /theatre/cases/{id}/consent/emergency-exception) ──
--    consent authority under emergency — SURGEON + CONSULTANT + ANAESTHETIST, min_loa floor.
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-consent-exception-surgeon',
 'SURGEON: record an emergency consent-exception (two-clinician / audited).',
 'PROVIDER', 'SURGEON', 'emergency-exception', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/consent/emergency-exception", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-consent-exception-consultant',
 'CONSULTANT: record an emergency consent-exception.',
 'PROVIDER', 'CONSULTANT', 'emergency-exception', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/consent/emergency-exception", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-consent-exception-anaesthetist',
 'ANAESTHETIST: record an emergency consent-exception.',
 'PROVIDER', 'ANAESTHETIST', 'emergency-exception', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 52, '{"path_contains": "/consent/emergency-exception", "min_loa": 2}', true)

ON CONFLICT DO NOTHING;
