-- ============================================================================
-- V304 — reachability for the surgical course-of-care routes (parity close Wave A):
--
--   1. PUT/GET  /internal/v1/surgery/episodes/{id}/prehab
--   2. POST/GET /internal/v1/surgery/episodes/{id}/complications
--      PUT      /internal/v1/surgery/episodes/{id}/complications/{pathwayId}
--      POST     .../complications/{pathwayId}/grade|disclose|close
--   3. POST/GET /internal/v1/surgery/episodes/{id}/longitudinal-objects
--      POST     .../longitudinal-objects/{objectId}/remove|revise
--   4. PUT/GET  /internal/v1/surgery/episodes/{id}/followup
--   5. POST/GET /internal/v1/surgery/episodes/{id}/waitlist-revalidation
--
-- Plus a theatre seed row needed by Wave B3 (site-marking body maps):
--   6. POST /internal/v1/theatre/cases/{id}/site-side
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Next free number after V303.
--
-- ══ THE TWO KNOWN PDP DEFECTS, designed around exactly as V300-V303 did ══
-- * Negatives are modelled as CORRECT-CADRE-ONLY ALLOW → NO_ALLOW_RULE, never as a path-pinned
--   DENY row (a conditional DENY that fails its own pin is skipped, not enforced).
-- * pathContainsSegment is segment-bounded and a TRAILING-SLASH pin can never match — every pin
--   below omits the trailing slash.
--
-- ══ ROUTE SHAPES ══ proven in SurgeryReachabilityRouteShapeTest against the real
-- deriveResourceType, not asserted from this comment:
--   .../episodes/{uuid}/prehab                         → "prehab"
--   .../episodes/{uuid}/complications                  → "complications"
--   .../episodes/{uuid}/complications/{uuid}/grade     → "grade"
--   .../episodes/{uuid}/complications/{uuid}/disclose  → "disclose"
--   .../episodes/{uuid}/complications/{uuid}/close     → "close"
--   .../episodes/{uuid}/longitudinal-objects           → "longitudinal-objects"
--   .../episodes/{uuid}/longitudinal-objects/{uuid}/remove → "remove"
--   .../episodes/{uuid}/longitudinal-objects/{uuid}/revise → "revise"
--   .../episodes/{uuid}/followup                       → "followup"
--   .../episodes/{uuid}/waitlist-revalidation          → "waitlist-revalidation"
--   .../theatre/cases/{uuid}/site-side                 → "site-side"
-- Pathway and object ids are opaque UUIDs, so free-text codes never become the derived type.
--
-- ══ COLLISION SAFETY ══ "remove"/"revise" already have V302 inventory rows pinned to
-- "/inventory/implants"; "close"/"grade"/"disclose" are generic English. Every surgery row
-- therefore pins "/surgery/episodes" — the episode-scoped family only. The site-side row pins
-- "/theatre/cases" so it cannot collide with any future /surgery path of the same name.
--
-- ══ ROLE SCOPING ══ (closed vocabulary — every role already exists in sibling migrations)
--   * READs (prehab/complications/longitudinal-objects/followup/waitlist-revalidation): the
--     surgical care team including NURSE — same read cadre V302 grants the episode itself
--     (SURGEON, CONSULTANT, CLINICIAN, DOCTOR, NURSE).
--   * prehab / followup / waitlist-revalidation WRITE + complications recognise/update +
--     longitudinal place: SURGEON, CONSULTANT, CLINICIAN (coordination-level clinical acts).
--   * grade / disclose / close and longitudinal remove / revise: senior-gated
--     (SURGEON, CONSULTANT) — operative-judgement acts.
--   * site-side POST: SURGEON, NURSE, CONSULTANT (table-side confirmation of marked site/side).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ════════════════════ 1. Prehabilitation — PUT/GET .../{id}/prehab ════════════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-write-surgeon',
 'SURGEON: record/refine a prehabilitation or optimisation item on a surgical episode.',
 'PROVIDER', 'SURGEON', 'prehab', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-write-consultant',
 'CONSULTANT: record/refine a prehabilitation or optimisation item.',
 'PROVIDER', 'CONSULTANT', 'prehab', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-write-clinician',
 'CLINICIAN: record/refine a prehabilitation or optimisation item.',
 'PROVIDER', 'CLINICIAN', 'prehab', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-read-surgeon',
 'SURGEON: read prehabilitation items on a surgical episode.',
 'PROVIDER', 'SURGEON', 'prehab', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-read-consultant',
 'CONSULTANT: read prehabilitation items on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'prehab', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-read-clinician',
 'CLINICIAN: read prehabilitation items on a surgical episode.',
 'PROVIDER', 'CLINICIAN', 'prehab', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-read-doctor',
 'DOCTOR: read prehabilitation items on a surgical episode.',
 'PROVIDER', 'DOCTOR', 'prehab', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-prehab-read-nurse',
 'NURSE: read prehabilitation items (perioperative preparation).',
 'PROVIDER', 'NURSE', 'prehab', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════ 2. Complication pathways — collection + grade/disclose/close ════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-recognise-surgeon',
 'SURGEON: recognise a complication pathway on a surgical episode.',
 'PROVIDER', 'SURGEON', 'complications', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-recognise-consultant',
 'CONSULTANT: recognise a complication pathway on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'complications', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-recognise-clinician',
 'CLINICIAN: recognise a complication pathway on a surgical episode.',
 'PROVIDER', 'CLINICIAN', 'complications', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-read-surgeon',
 'SURGEON: list complication pathways on a surgical episode.',
 'PROVIDER', 'SURGEON', 'complications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-read-consultant',
 'CONSULTANT: list complication pathways on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'complications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-read-clinician',
 'CLINICIAN: list complication pathways on a surgical episode.',
 'PROVIDER', 'CLINICIAN', 'complications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-read-doctor',
 'DOCTOR: list complication pathways on a surgical episode.',
 'PROVIDER', 'DOCTOR', 'complications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-read-nurse',
 'NURSE: list complication pathways on a surgical episode.',
 'PROVIDER', 'NURSE', 'complications', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-update-surgeon',
 'SURGEON: update an open complication pathway (ownership, notes, status fields).',
 'PROVIDER', 'SURGEON', 'complications', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-update-consultant',
 'CONSULTANT: update an open complication pathway.',
 'PROVIDER', 'CONSULTANT', 'complications', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-update-clinician',
 'CLINICIAN: update an open complication pathway.',
 'PROVIDER', 'CLINICIAN', 'complications', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- grade / disclose / close — senior-gated
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-grade-surgeon',
 'SURGEON: grade a complication pathway (Clavien-Dindo / specialty grade).',
 'PROVIDER', 'SURGEON', 'grade', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-grade-consultant',
 'CONSULTANT: grade a complication pathway.',
 'PROVIDER', 'CONSULTANT', 'grade', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-disclose-surgeon',
 'SURGEON: disclose a complication to the person / next of kin (records who and when).',
 'PROVIDER', 'SURGEON', 'disclose', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-disclose-consultant',
 'CONSULTANT: disclose a complication to the person / next of kin.',
 'PROVIDER', 'CONSULTANT', 'disclose', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-close-surgeon',
 'SURGEON: close a complication pathway (contribute outcome into PCT when eligible).',
 'PROVIDER', 'SURGEON', 'close', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-complications-close-consultant',
 'CONSULTANT: close a complication pathway.',
 'PROVIDER', 'CONSULTANT', 'close', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════ 3. Longitudinal objects (drain/stoma/wound) ════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-place-surgeon',
 'SURGEON: place a longitudinal object (drain/stoma/wound) on a surgical episode.',
 'PROVIDER', 'SURGEON', 'longitudinal-objects', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-place-consultant',
 'CONSULTANT: place a longitudinal object on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'longitudinal-objects', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-place-clinician',
 'CLINICIAN: place a longitudinal object on a surgical episode.',
 'PROVIDER', 'CLINICIAN', 'longitudinal-objects', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-read-surgeon',
 'SURGEON: list longitudinal objects on a surgical episode.',
 'PROVIDER', 'SURGEON', 'longitudinal-objects', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-read-consultant',
 'CONSULTANT: list longitudinal objects on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'longitudinal-objects', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-read-clinician',
 'CLINICIAN: list longitudinal objects on a surgical episode.',
 'PROVIDER', 'CLINICIAN', 'longitudinal-objects', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-read-doctor',
 'DOCTOR: list longitudinal objects on a surgical episode.',
 'PROVIDER', 'DOCTOR', 'longitudinal-objects', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-read-nurse',
 'NURSE: list longitudinal objects (ward drain/stoma/wound care).',
 'PROVIDER', 'NURSE', 'longitudinal-objects', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- remove / revise — senior-gated; pin keeps them off inventory implant remove/revise
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-remove-surgeon',
 'SURGEON: remove a longitudinal object (drain/stoma/wound) from a surgical episode.',
 'PROVIDER', 'SURGEON', 'remove', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-remove-consultant',
 'CONSULTANT: remove a longitudinal object from a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'remove', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-revise-surgeon',
 'SURGEON: revise a longitudinal object on a surgical episode.',
 'PROVIDER', 'SURGEON', 'revise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-longitudinal-revise-consultant',
 'CONSULTANT: revise a longitudinal object on a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'revise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════════════ 4. Follow-up — PUT/GET .../{id}/followup ════════════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-write-surgeon',
 'SURGEON: record/refine the surgical discharge and follow-up plan.',
 'PROVIDER', 'SURGEON', 'followup', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-write-consultant',
 'CONSULTANT: record/refine the surgical discharge and follow-up plan.',
 'PROVIDER', 'CONSULTANT', 'followup', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-write-clinician',
 'CLINICIAN: record/refine the surgical discharge and follow-up plan.',
 'PROVIDER', 'CLINICIAN', 'followup', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-read-surgeon',
 'SURGEON: read the surgical discharge and follow-up plan.',
 'PROVIDER', 'SURGEON', 'followup', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-read-consultant',
 'CONSULTANT: read the surgical discharge and follow-up plan.',
 'PROVIDER', 'CONSULTANT', 'followup', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-read-clinician',
 'CLINICIAN: read the surgical discharge and follow-up plan.',
 'PROVIDER', 'CLINICIAN', 'followup', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-read-doctor',
 'DOCTOR: read the surgical discharge and follow-up plan.',
 'PROVIDER', 'DOCTOR', 'followup', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-followup-read-nurse',
 'NURSE: read the surgical discharge and follow-up plan.',
 'PROVIDER', 'NURSE', 'followup', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════ 5. Waiting-list clinical revalidation ════════════

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-write-surgeon',
 'SURGEON: append a waiting-list clinical revalidation on a surgical episode.',
 'PROVIDER', 'SURGEON', 'waitlist-revalidation', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-write-consultant',
 'CONSULTANT: append a waiting-list clinical revalidation.',
 'PROVIDER', 'CONSULTANT', 'waitlist-revalidation', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-write-clinician',
 'CLINICIAN: append a waiting-list clinical revalidation.',
 'PROVIDER', 'CLINICIAN', 'waitlist-revalidation', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-read-surgeon',
 'SURGEON: list waiting-list clinical revalidations on a surgical episode.',
 'PROVIDER', 'SURGEON', 'waitlist-revalidation', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-read-consultant',
 'CONSULTANT: list waiting-list clinical revalidations.',
 'PROVIDER', 'CONSULTANT', 'waitlist-revalidation', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-read-clinician',
 'CLINICIAN: list waiting-list clinical revalidations.',
 'PROVIDER', 'CLINICIAN', 'waitlist-revalidation', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-read-doctor',
 'DOCTOR: list waiting-list clinical revalidations.',
 'PROVIDER', 'DOCTOR', 'waitlist-revalidation', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-waitlist-revalidation-read-nurse',
 'NURSE: list waiting-list clinical revalidations.',
 'PROVIDER', 'NURSE', 'waitlist-revalidation', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════ 6. Theatre site-side confirm (Wave B3 seed) ════════════
-- POST .../theatre/cases/{id}/site-side — table-side confirmation of marked site/side.
-- Pinned "/theatre/cases" so it cannot collide with any surgery path of the same name.

('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-site-side-confirm-surgeon',
 'SURGEON: confirm anatomical site and laterality (site-side marking) on a theatre case.',
 'PROVIDER', 'SURGEON', 'site-side', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-site-side-confirm-nurse',
 'NURSE: confirm anatomical site and laterality (site-side marking) on a theatre case.',
 'PROVIDER', 'NURSE', 'site-side', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-site-side-confirm-consultant',
 'CONSULTANT: confirm anatomical site and laterality (site-side marking) on a theatre case.',
 'PROVIDER', 'CONSULTANT', 'site-side', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true)
ON CONFLICT DO NOTHING;
