-- ============================================================================
-- V300 — procedures-service platform-layer ext_authz ALLOW rules.
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3).
--
-- Gates the BFF's new /internal/v1/procedures/** proxy (ProceduresController, P-R.5) at the
-- Envoy ext_authz edge — the same edge that gates /internal/v1/theatre/**. These are the
-- FIRST authz rows for procedures-service; nothing gated it before because nothing proxied
-- it before.
--
-- BROAD ROLE SET DELIBERATELY. Unlike theatre (surgical/perioperative, SURGEON+ANAESTHETIST),
-- procedures-service is the pipeline's cross-cutting platform layer — its own spec (§0) states
-- it "must be reusable by Emergency Care, Medicine, Surgery, Theatre, Anaesthesia, Paediatrics,
-- Reproductive and Maternity care, Critical Care, Mental Health, Dentistry, Rehabilitation,
-- Nursing, allied health, Laboratory, and imaging and interventional services." Restricting
-- these read/evaluate endpoints to a surgical role set would work against that design intent
-- before a single specialty pack has been built. CLINICIAN, DOCTOR, NURSE, CONSULTANT,
-- WARD_MANAGER — the generic clinical roles already used elsewhere in this file — cover it.
--
-- ALL THREE ROUTES ARE READ-ONLY / EVALUATE-ONLY (engine-not-store, ADR decision 2). None
-- writes to a clinical record: the catalogue is reference data, appropriateness/evaluate
-- returns a judgement with nothing persisted, competence resolves a read-only VARAPI lookup.
-- So the access question here is "who may consult the pipeline", not "who may act on a
-- patient" — the latter gate is enforced downstream, at the point a procedure actually starts
-- (inpatient-service's site-and-side and consent gates, P4/P5).
--
-- ══ PATH-PIN CORRECTNESS — a real defect found and avoided, not copied ══
-- V029/V030 pin path_contains with a TRAILING SLASH, e.g. "/theatre/cases/". PolicyEngine's
-- pathContainsSegment(path, pin) requires the character immediately after the match to be '/'
-- or end-of-string. For a trailing-slash pin, that character is the FIRST character of the
-- next path segment (e.g. an id) — never '/' and never end-of-string — so the match ALWAYS
-- FAILS. Verified by reading pathContainsSegment directly
-- (tshepo-authz-service .../core/PolicyEngine.java:1239), not inferred from the theatre
-- programme's comment flagging it as "possible". Every pin below omits the trailing slash.
--
-- ══ THE CATALOGUE-DETAIL ROUTE SHAPE — designed around a second, related defect ══
-- AuthzInternalRequest.deriveResourceType walks the path backward from its last segment and
-- returns the first one that is not blank, not "v1", not "api", and not a 36-character UUID.
-- procedures.procedure_definition.definition_code values are human-readable strings like
-- "PROC-LAPAROTOMY" — not UUIDs — so a route shaped .../catalogue/{definitionCode} would
-- derive resource_type = "PROC-LAPAROTOMY" (a different value per procedure!) instead of
-- "catalogue", and no rule below would ever be found as a candidate. This is why the BFF
-- proxy (P-R.5) exposes catalogue-detail as its OWN fixed final segment with the code as a
-- query parameter — /internal/v1/procedures/catalogue-detail?code=... — rather than as a
-- REST path variable. Fixing this in PolicyEngine's shared derivation logic would be a
-- estate-wide change with its own review; shaping the one route under my control avoids it
-- entirely.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Catalogue search — GET /internal/v1/procedures/catalogue ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-search-clinician',
 'CLINICIAN: search the procedure catalogue.',
 'PROVIDER', 'CLINICIAN', 'catalogue', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-search-doctor',
 'DOCTOR: search the procedure catalogue.',
 'PROVIDER', 'DOCTOR', 'catalogue', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-search-nurse',
 'NURSE: search the procedure catalogue.',
 'PROVIDER', 'NURSE', 'catalogue', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-search-consultant',
 'CONSULTANT: search the procedure catalogue.',
 'PROVIDER', 'CONSULTANT', 'catalogue', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-search-ward-manager',
 'WARD_MANAGER: search the procedure catalogue (scheduling/resource planning).',
 'PROVIDER', 'WARD_MANAGER', 'catalogue', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue"}', true),

-- ── Catalogue detail — GET /internal/v1/procedures/catalogue-detail?code=... ──
-- Separate resource_type from search because it is a separate final path segment (see the
-- catalogue-detail note above) — NOT because access should differ; the role set is identical.
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-detail-clinician',
 'CLINICIAN: view a single catalogue entry and its requirements.',
 'PROVIDER', 'CLINICIAN', 'catalogue-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-detail-doctor',
 'DOCTOR: view a single catalogue entry and its requirements.',
 'PROVIDER', 'DOCTOR', 'catalogue-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-detail-nurse',
 'NURSE: view a single catalogue entry and its requirements.',
 'PROVIDER', 'NURSE', 'catalogue-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-detail-consultant',
 'CONSULTANT: view a single catalogue entry and its requirements.',
 'PROVIDER', 'CONSULTANT', 'catalogue-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-catalogue-detail-ward-manager',
 'WARD_MANAGER: view a single catalogue entry and its requirements.',
 'PROVIDER', 'WARD_MANAGER', 'catalogue-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/catalogue-detail"}', true),

-- ── Appropriateness evaluation — POST /internal/v1/procedures/appropriateness/evaluate ──
-- resource_type is 'evaluate' because that is deriveResourceType's answer for this path
-- (last non-UUID, non-v1/api segment) — matches procedures-service's own controller mapping,
-- so no route-shape workaround was needed here.
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-appropriateness-evaluate-clinician',
 'CLINICIAN: run an appropriateness and duplication check before requesting a procedure.',
 'PROVIDER', 'CLINICIAN', 'evaluate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/appropriateness"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-appropriateness-evaluate-doctor',
 'DOCTOR: run an appropriateness and duplication check before requesting a procedure.',
 'PROVIDER', 'DOCTOR', 'evaluate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/appropriateness"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-appropriateness-evaluate-nurse',
 'NURSE: run an appropriateness and duplication check before requesting a procedure.',
 'PROVIDER', 'NURSE', 'evaluate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/appropriateness"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-appropriateness-evaluate-consultant',
 'CONSULTANT: run an appropriateness and duplication check before requesting a procedure.',
 'PROVIDER', 'CONSULTANT', 'evaluate', 'POST', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/appropriateness"}', true),

-- ── Competence resolution — GET /internal/v1/procedures/competence?providerId=&definitionCode= ──
-- Query-param based already (procedures-service's own CompetenceController), so the last path
-- segment is always the fixed word "competence" — no route-shape workaround needed.
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-competence-clinician',
 'CLINICIAN: resolve whether a provider may perform a procedure, and whether alone.',
 'PROVIDER', 'CLINICIAN', 'competence', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/competence"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-competence-doctor',
 'DOCTOR: resolve whether a provider may perform a procedure, and whether alone.',
 'PROVIDER', 'DOCTOR', 'competence', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/competence"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-competence-consultant',
 'CONSULTANT: resolve whether a provider may perform a procedure, and whether alone.',
 'PROVIDER', 'CONSULTANT', 'competence', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/competence"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-competence-ward-manager',
 'WARD_MANAGER: resolve whether a provider may perform a procedure, and whether alone (rostering).',
 'PROVIDER', 'WARD_MANAGER', 'competence', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/competence"}', true);
