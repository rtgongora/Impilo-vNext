-- ============================================================================
-- V303 — reachability for the surgical routes added by the completion wave (SB-9/demo 4):
--
--   1. POST   /internal/v1/surgery/episodes/{id}/reopen               (V010, demonstration 9)
--   2. GET    /internal/v1/surgery/episodes/{id}/specialties          (V011, demonstration 4)
--   3. POST   /internal/v1/surgery/episodes/{id}/specialties
--   4. DELETE /internal/v1/surgery/episodes/{id}/specialties?specialty=...
--   5. POST   /internal/v1/surgery/episodes/{id}/specialties/lead
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Next free number after V302.
--
-- ══ NO NEW ROWS ARE NEEDED FOR THE OTHER COMPLETION-WAVE WORK ══
-- * The MDT decision forum (surgery V012) adds FIELDS to the existing PUT/GET
--   .../{id}/decision body — same route, already gated by V302's surgery-decision-* rows.
-- * The SB-5 operative-record depth (inpatient V304) adds FIELDS to the existing theatre
--   operative-note route, already gated.
-- * Return-to-theatre (inpatient V305) adds FIELDS to POST /theatre/cases/{id}/return-to-theatre,
--   already gated by V034's return-to-theatre SURGEON/CONSULTANT rows. Confirmed present rather
--   than assumed: V034 lines 84-92.
--
-- ══ THE TWO KNOWN PDP DEFECTS, designed around exactly as V300-V302 did ══
-- * Negatives are modelled as CORRECT-CADRE-ONLY ALLOW → NO_ALLOW_RULE, never as a path-pinned
--   DENY row (a conditional DENY that fails its own pin is skipped, not enforced).
-- * pathContainsSegment is segment-bounded and a TRAILING-SLASH pin can never match — every pin
--   below omits the trailing slash.
--
-- ══ ROUTE SHAPES ══ proven in SurgeryReachabilityRouteShapeTest against the real
-- deriveResourceType, not asserted from this comment:
--   .../episodes/{uuid}/reopen            → "reopen"
--   .../episodes/{uuid}/specialties       → "specialties"  (GET, POST and DELETE alike)
--   .../episodes/{uuid}/specialties/lead  → "lead"
-- The DELETE deliberately carries the specialty as ?specialty=, NOT as a final segment: as a
-- segment it would derive "GENERAL_SURGERY" and no row could ever match it. That is the
-- free-text-code-in-path trap; the controller javadoc records the same reason.
--
-- ══ COLLISION SAFETY ══ "specialties" is a generic word and surgery-service already serves
-- /internal/v1/surgery/specialties/indications and /templates — but those derive "indications"
-- and "templates", never "specialties", so there is no overlap. "reopen" and "lead" are generic
-- English. Every row therefore pins "/surgery/episodes", the episode-scoped family only.
-- Checked in the other direction too, which is the one that actually leaks: the only pre-existing
-- row on any of these three resource types anywhere in the estate is V021's FACILITY_SAFETY_FOCAL
-- 'reopen' DENY pinned to "/rito/cases", which cannot match a /surgery/episodes path.
--
-- ══ ROLE SCOPING ══ (closed vocabulary — every role already exists in sibling migrations)
--   * reopen: senior-gated (SURGEON, CONSULTANT). Returning a patient to theatre is the
--     consultant-level act; the service additionally requires a non-blank reason and stamps
--     reopened_by/at, and the DB CHECK refuses an unaudited REOPENED row.
--   * specialties READ: the full surgical care team including NURSE — theatre nursing must see
--     which teams are operating on the case to prepare for them. Same read cadre V302 grants
--     the episode itself.
--   * specialties WRITE (add/remove/lead handover): SURGEON and CONSULTANT. Which specialty
--     leads a shared case is an operative-responsibility decision, not a coordination one, so
--     it is NOT granted to CLINICIAN as episode-open is.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Reopen for a return to theatre — POST .../{id}/reopen — senior-gated ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-reopen-surgeon',
 'SURGEON: reopen a surgical episode for a return to theatre (records reason, who and when).',
 'PROVIDER', 'SURGEON', 'reopen', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-reopen-consultant',
 'CONSULTANT: reopen a surgical episode for a return to theatre.',
 'PROVIDER', 'CONSULTANT', 'reopen', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Read every specialty on the case — GET .../{id}/specialties ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-read-surgeon',
 'SURGEON: read every specialty on a shared surgical case.',
 'PROVIDER', 'SURGEON', 'specialties', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-read-consultant',
 'CONSULTANT: read every specialty on a shared surgical case.',
 'PROVIDER', 'CONSULTANT', 'specialties', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-read-clinician',
 'CLINICIAN: read every specialty on a shared surgical case.',
 'PROVIDER', 'CLINICIAN', 'specialties', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-read-doctor',
 'DOCTOR: read every specialty on a shared surgical case.',
 'PROVIDER', 'DOCTOR', 'specialties', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-read-nurse',
 'NURSE: read every specialty on a shared surgical case (theatre nursing prepares for each team).',
 'PROVIDER', 'NURSE', 'specialties', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Add a joining specialty — POST .../{id}/specialties — senior-gated ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-add-surgeon',
 'SURGEON: add a joining specialty to a shared surgical case.',
 'PROVIDER', 'SURGEON', 'specialties', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-add-consultant',
 'CONSULTANT: add a joining specialty to a shared surgical case.',
 'PROVIDER', 'CONSULTANT', 'specialties', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Remove a joining specialty — DELETE .../{id}/specialties?specialty=... — senior-gated ──
-- The service refuses to remove the LEAD; these rows gate who may remove at all.
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-remove-surgeon',
 'SURGEON: remove a joining specialty from a shared surgical case (the lead cannot be removed).',
 'PROVIDER', 'SURGEON', 'specialties', 'DELETE', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-remove-consultant',
 'CONSULTANT: remove a joining specialty from a shared surgical case.',
 'PROVIDER', 'CONSULTANT', 'specialties', 'DELETE', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Hand the lead over — POST .../{id}/specialties/lead — senior-gated ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-lead-surgeon',
 'SURGEON: hand the lead specialty of a shared surgical case to another team.',
 'PROVIDER', 'SURGEON', 'lead', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-specialties-lead-consultant',
 'CONSULTANT: hand the lead specialty of a shared surgical case to another team.',
 'PROVIDER', 'CONSULTANT', 'lead', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true)
ON CONFLICT DO NOTHING;
