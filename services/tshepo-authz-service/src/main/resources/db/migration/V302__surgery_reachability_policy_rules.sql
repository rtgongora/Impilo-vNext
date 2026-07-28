-- ============================================================================
-- V302 — Consolidated reachability (SB-3): ext_authz ALLOW rules for the four backend
-- surfaces this wave wires through the BFF for the first time:
--
--   1. surgery-service surgical episodes (S1) + assessment (S2) + decision (S3)
--      under /internal/v1/surgery/episodes/**  (SurgeryController, new BFF proxy)
--   2. procedures-service analytics indicator catalogue (P14)
--      under /internal/v1/procedures/analytics/**  (ProceduresController extension)
--   3. inpatient-service theatre specimen custody (P8, pipeline §13)
--      POST /internal/v1/theatre/cases/{id}/specimens/{sid}/{collect|confirm-label|receive|adequacy}
--   4. inventory-service implant lifecycle (P8, pipeline §14)
--      under /internal/v1/inventory/implants/**  (InventoryController extension; the BFF
--      calls inventory-service's own /v1/internal/implants/** routes directly — inpatient's
--      theatre implant proxy has no remove/revise/SoR-recall path and inpatient-service
--      source is out of scope for this wave)
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Next free number after V301.
--
-- ══ THE TWO KNOWN PDP DEFECTS, designed around exactly as V300/V301 did ══
-- * path_contains is evaluated only when a rule actually fires, and the estate's negatives are
--   therefore modelled as CORRECT-CADRE-ONLY ALLOW → NO_ALLOW_RULE (a DENY verdict), never as a
--   path-pinned DENY row (a conditional DENY that fails its own pin is skipped, not enforced).
-- * pathContainsSegment is segment-bounded and a TRAILING-SLASH pin can never match — every pin
--   below omits the trailing slash (V300's header has the full defect walk-through).
--
-- ══ ROUTE SHAPES ══
-- Surgical episode ids are opaque UUIDs in path segments; AuthzInternalRequest.deriveResourceType
-- skips 36-char UUID segments, so .../episodes/{uuid} still derives "episodes",
-- .../episodes/{uuid}/decision derives "decision", and so on — no free-text-code-in-path trap
-- here (SurgicalEpisodeController's own javadoc states this was deliberate from S1). All
-- free-text codes on these surfaces travel as query parameters (?code=, ?udi=, ?lot=,
-- ?subjectCpid=), which never reach the segment walk. Proven against the real derivation in
-- SurgeryReachabilityRouteShapeTest, not asserted from comments.
--
-- ══ COLLISION SAFETY ══ Generic final segments abound here ("episodes" collides with
-- inpatient's /internal/v1/procedures/episodes, "decision"/"assessment"/"transition"/"collect"/
-- "receive"/"recall" are generic words). Every rule pins its own path family:
-- "/surgery/episodes", "/procedures/analytics", "/theatre/cases", "/inventory/implants".
--
-- ══ ROLE SCOPING ══ (closed vocabulary only — every role below already exists in this file's
-- sibling migrations; no new cadre is invented)
--   * surgery episodes: open/read by the surgical decision-making cadre (SURGEON, CONSULTANT,
--     CLINICIAN, DOCTOR; reads add NURSE — ward nursing needs to see the episode and its
--     assessment). transition + the S3 decision WRITE are senior-gated (SURGEON, CONSULTANT):
--     the decision to operate is the consultant-level act the surgical pack §8 records.
--   * analytics indicators: the same broad platform role set as V300/V301 (CLINICIAN, DOCTOR,
--     NURSE, CONSULTANT, WARD_MANAGER) — a read-only catalogue of indicator definitions and
--     their computation status, not patient data.
--   * specimen custody: collection + label confirmation happen at the table (SURGEON, NURSE);
--     custody receipt is signed by the receiving side (LAB_TECH, or the NURSE runner handing
--     over); adequacy is a laboratory judgement (LAB_TECH, CONSULTANT for the reporting
--     pathologist cadre — no narrower pathology role exists in the vocabulary).
--   * implant lifecycle: removal/revision are operative acts (SURGEON, CONSULTANT); the SoR
--     recall trace is granted to the perioperative + governance cadres that run a recall
--     (SURGEON, CONSULTANT, CLINICIAN, WARD_MANAGER).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ════════════════════════ 1. surgery-service (S1-S3) ════════════════════════

-- ── Open a surgical episode — POST /internal/v1/surgery/episodes ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-open-surgeon',
 'SURGEON: open a surgical episode (CC-5 anchored; contributes the operative indication to PCT).',
 'PROVIDER', 'SURGEON', 'episodes', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-open-consultant',
 'CONSULTANT: open a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'episodes', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-open-clinician',
 'CLINICIAN: open a surgical episode (referral into the surgical pathway).',
 'PROVIDER', 'CLINICIAN', 'episodes', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-open-doctor',
 'DOCTOR: open a surgical episode (referral into the surgical pathway).',
 'PROVIDER', 'DOCTOR', 'episodes', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Read episodes (list by subject + detail; UUID segment skipped) — GET /internal/v1/surgery/episodes[/{id}] ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-read-surgeon',
 'SURGEON: read surgical episodes.',
 'PROVIDER', 'SURGEON', 'episodes', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-read-consultant',
 'CONSULTANT: read surgical episodes.',
 'PROVIDER', 'CONSULTANT', 'episodes', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-read-clinician',
 'CLINICIAN: read surgical episodes.',
 'PROVIDER', 'CLINICIAN', 'episodes', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-read-doctor',
 'DOCTOR: read surgical episodes.',
 'PROVIDER', 'DOCTOR', 'episodes', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-read-nurse',
 'NURSE: read surgical episodes (ward preparation and perioperative nursing).',
 'PROVIDER', 'NURSE', 'episodes', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Link the executing procedure episode — POST .../{id}/link-procedure-episode ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-link-surgeon',
 'SURGEON: link the theatre/procedure episode that executes this surgical episode.',
 'PROVIDER', 'SURGEON', 'link-procedure-episode', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-link-consultant',
 'CONSULTANT: link the executing procedure episode.',
 'PROVIDER', 'CONSULTANT', 'link-procedure-episode', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-link-clinician',
 'CLINICIAN: link the executing procedure episode (theatre coordination).',
 'PROVIDER', 'CLINICIAN', 'link-procedure-episode', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Episode state transition — POST .../{id}/transition — senior-gated ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-transition-surgeon',
 'SURGEON: transition a surgical episode (ASSESSMENT/LISTED_FOR_SURGERY/OPERATED/CLOSED/ABANDONED).',
 'PROVIDER', 'SURGEON', 'transition', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-episode-transition-consultant',
 'CONSULTANT: transition a surgical episode.',
 'PROVIDER', 'CONSULTANT', 'transition', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── General surgical assessment WRITE — PUT .../{id}/assessment (S2) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-write-surgeon',
 'SURGEON: record/refine the general surgical assessment.',
 'PROVIDER', 'SURGEON', 'assessment', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-write-consultant',
 'CONSULTANT: record/refine the general surgical assessment.',
 'PROVIDER', 'CONSULTANT', 'assessment', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-write-clinician',
 'CLINICIAN: record/refine the general surgical assessment.',
 'PROVIDER', 'CLINICIAN', 'assessment', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-write-doctor',
 'DOCTOR: record/refine the general surgical assessment.',
 'PROVIDER', 'DOCTOR', 'assessment', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── General surgical assessment READ — GET .../{id}/assessment ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-read-surgeon',
 'SURGEON: read the general surgical assessment.',
 'PROVIDER', 'SURGEON', 'assessment', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-read-consultant',
 'CONSULTANT: read the general surgical assessment.',
 'PROVIDER', 'CONSULTANT', 'assessment', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-read-clinician',
 'CLINICIAN: read the general surgical assessment.',
 'PROVIDER', 'CLINICIAN', 'assessment', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-read-doctor',
 'DOCTOR: read the general surgical assessment.',
 'PROVIDER', 'DOCTOR', 'assessment', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-assessment-read-nurse',
 'NURSE: read the general surgical assessment (perioperative nursing preparation).',
 'PROVIDER', 'NURSE', 'assessment', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Surgical decision WRITE — PUT .../{id}/decision (S3) — senior-gated ──
-- The decision to operate/not operate/defer is the consultant-level act §8 records; the
-- 3-way final-decision pairing is enforced in-service, these rows gate who may write at all.
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-write-surgeon',
 'SURGEON: record/refine the surgical decision-making record.',
 'PROVIDER', 'SURGEON', 'decision', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-write-consultant',
 'CONSULTANT: record/refine the surgical decision-making record.',
 'PROVIDER', 'CONSULTANT', 'decision', 'PUT', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ── Surgical decision READ — GET .../{id}/decision ──
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-read-surgeon',
 'SURGEON: read the surgical decision-making record.',
 'PROVIDER', 'SURGEON', 'decision', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-read-consultant',
 'CONSULTANT: read the surgical decision-making record.',
 'PROVIDER', 'CONSULTANT', 'decision', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-read-clinician',
 'CLINICIAN: read the surgical decision-making record.',
 'PROVIDER', 'CLINICIAN', 'decision', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-read-doctor',
 'DOCTOR: read the surgical decision-making record.',
 'PROVIDER', 'DOCTOR', 'decision', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'surgery-decision-read-nurse',
 'NURSE: read the surgical decision-making record.',
 'PROVIDER', 'NURSE', 'decision', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/surgery/episodes"}', true),

-- ════════════════════ 2. procedures-service analytics (P14) ════════════════════

-- ── Indicator catalogue — GET /internal/v1/procedures/analytics/indicators ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicators-clinician',
 'CLINICIAN: view the pipeline analytics indicator catalogue and its computation status.',
 'PROVIDER', 'CLINICIAN', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicators-doctor',
 'DOCTOR: view the pipeline analytics indicator catalogue.',
 'PROVIDER', 'DOCTOR', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicators-nurse',
 'NURSE: view the pipeline analytics indicator catalogue.',
 'PROVIDER', 'NURSE', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicators-consultant',
 'CONSULTANT: view the pipeline analytics indicator catalogue.',
 'PROVIDER', 'CONSULTANT', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicators-ward-manager',
 'WARD_MANAGER: view the pipeline analytics indicator catalogue (service planning).',
 'PROVIDER', 'WARD_MANAGER', 'indicators', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),

-- ── One indicator — GET /internal/v1/procedures/analytics/indicator?code=... ──
-- Query-param code shape from the start (P14), same as every controller in the programme.
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicator-clinician',
 'CLINICIAN: view one analytics indicator definition.',
 'PROVIDER', 'CLINICIAN', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicator-doctor',
 'DOCTOR: view one analytics indicator definition.',
 'PROVIDER', 'DOCTOR', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicator-nurse',
 'NURSE: view one analytics indicator definition.',
 'PROVIDER', 'NURSE', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicator-consultant',
 'CONSULTANT: view one analytics indicator definition.',
 'PROVIDER', 'CONSULTANT', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-analytics-indicator-ward-manager',
 'WARD_MANAGER: view one analytics indicator definition.',
 'PROVIDER', 'WARD_MANAGER', 'indicator', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/analytics"}', true),

-- ════════════════ 3. theatre specimen custody (P8, pipeline §13) ════════════════
-- POST .../cases/{id}/specimens/{sid}/collect|confirm-label|receive|adequacy — both ids are
-- UUIDs, so the fixed action word is always the derived resource_type. Pinned "/theatre/cases"
-- exactly as V034/V035 pin this family (no trailing slash).

('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-collect-surgeon',
 'SURGEON: attest specimen collection (container/fixative) at the table.',
 'PROVIDER', 'SURGEON', 'collect', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-collect-nurse',
 'NURSE: attest specimen collection (scrub nurse).',
 'PROVIDER', 'NURSE', 'collect', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-confirm-label-surgeon',
 'SURGEON: confirm the specimen label against the patient and site.',
 'PROVIDER', 'SURGEON', 'confirm-label', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-confirm-label-nurse',
 'NURSE: confirm the specimen label (two-person check).',
 'PROVIDER', 'NURSE', 'confirm-label', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-receive-lab-tech',
 'LAB_TECH: sign custody receipt of a theatre specimen at the laboratory.',
 'PROVIDER', 'LAB_TECH', 'receive', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-receive-nurse',
 'NURSE: sign custody receipt (runner handover leg).',
 'PROVIDER', 'NURSE', 'receive', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-adequacy-lab-tech',
 'LAB_TECH: record specimen adequacy assessment.',
 'PROVIDER', 'LAB_TECH', 'adequacy', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/theatre/cases"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'theatre-specimen-adequacy-consultant',
 'CONSULTANT: record specimen adequacy assessment (reporting pathologist cadre — no narrower pathology role exists in the vocabulary).',
 'PROVIDER', 'CONSULTANT', 'adequacy', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/theatre/cases"}', true),

-- ════════════════ 4. inventory implant lifecycle (P8, pipeline §14) ════════════════
-- BFF routes /internal/v1/inventory/implants/{patientImplantId}/remove|revise (POST, UUID id)
-- and /internal/v1/inventory/implants/recall (GET, ?udi=&lot=). Distinct from the theatre
-- CASE-side recall projection (/theatre/implants/recall, inpatient-local): these hit the
-- inventory SoR, which also knows removal/revision status.

('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-remove-surgeon',
 'SURGEON: record removal of an implanted device (explant).',
 'PROVIDER', 'SURGEON', 'remove', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-remove-consultant',
 'CONSULTANT: record removal of an implanted device.',
 'PROVIDER', 'CONSULTANT', 'remove', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/inventory/implants"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-revise-surgeon',
 'SURGEON: record revision of an implanted device (explant + new implant in one act).',
 'PROVIDER', 'SURGEON', 'revise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-revise-consultant',
 'CONSULTANT: record revision of an implanted device.',
 'PROVIDER', 'CONSULTANT', 'revise', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 51, '{"path_contains": "/inventory/implants"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-recall-trace-surgeon',
 'SURGEON: trace a recalled UDI/lot against the inventory implant SoR.',
 'PROVIDER', 'SURGEON', 'recall', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-recall-trace-consultant',
 'CONSULTANT: trace a recalled UDI/lot against the inventory implant SoR.',
 'PROVIDER', 'CONSULTANT', 'recall', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-recall-trace-clinician',
 'CLINICIAN: trace a recalled UDI/lot against the inventory implant SoR.',
 'PROVIDER', 'CLINICIAN', 'recall', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'inventory-implant-recall-trace-ward-manager',
 'WARD_MANAGER: trace a recalled UDI/lot (recall response coordination).',
 'PROVIDER', 'WARD_MANAGER', 'recall', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/inventory/implants"}', true)
ON CONFLICT DO NOTHING;
