-- ============================================================================
-- V306 — procedures-service ext_authz ALLOW rules for Clavien-Dindo grades and
-- complication profiles (Wave W4 — catalogue risk profiles, NOT surgery pathway grading).
--
-- Backend already exists (ComplicationProfileController, P10). This migration only opens
-- the BFF-facing routes so the catalogue browser can resolve a definition's
-- complicationProfile linkage. Surgery episode pathway grading
-- (.../surgery/episodes/{id}/complications/.../grade) is a different resource family —
-- already gated elsewhere; do not confuse the two.
--
-- Same reasoning as V300/V301: procedures-service is the pipeline's cross-cutting platform
-- layer, so the broad clinical role set (CLINICIAN, DOCTOR, NURSE, CONSULTANT, WARD_MANAGER)
-- applies. Both routes are read-only (engine-not-store) — reference content, never an
-- actual complication occurrence.
--
-- ══ ROUTE SHAPE ══ Query-param code shape from the start (ComplicationProfileController) —
-- fixed final segments "clavien-dindo-grades" and "complication-profiles", so
-- deriveResourceType is stable. No path-variable workaround needed.
--
-- ══ PATH-PIN CORRECTNESS ══ Every pin omits the trailing slash (pathContainsSegment requires
-- '/' or end-of-string immediately after a match).
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Next free number after V305.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Clavien-Dindo grades — GET /internal/v1/procedures/clavien-dindo-grades ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-clavien-dindo-grades-clinician',
 'CLINICIAN: view the Clavien-Dindo severity grade catalogue.',
 'PROVIDER', 'CLINICIAN', 'clavien-dindo-grades', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/clavien-dindo-grades"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-clavien-dindo-grades-doctor',
 'DOCTOR: view the Clavien-Dindo severity grade catalogue.',
 'PROVIDER', 'DOCTOR', 'clavien-dindo-grades', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/clavien-dindo-grades"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-clavien-dindo-grades-nurse',
 'NURSE: view the Clavien-Dindo severity grade catalogue.',
 'PROVIDER', 'NURSE', 'clavien-dindo-grades', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/clavien-dindo-grades"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-clavien-dindo-grades-consultant',
 'CONSULTANT: view the Clavien-Dindo severity grade catalogue.',
 'PROVIDER', 'CONSULTANT', 'clavien-dindo-grades', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/clavien-dindo-grades"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-clavien-dindo-grades-ward-manager',
 'WARD_MANAGER: view the Clavien-Dindo severity grade catalogue.',
 'PROVIDER', 'WARD_MANAGER', 'clavien-dindo-grades', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/clavien-dindo-grades"}', true),

-- ── Complication profile — GET /internal/v1/procedures/complication-profiles?code=... ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-complication-profiles-clinician',
 'CLINICIAN: view a published complication profile and its anticipated classes.',
 'PROVIDER', 'CLINICIAN', 'complication-profiles', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/complication-profiles"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-complication-profiles-doctor',
 'DOCTOR: view a published complication profile and its anticipated classes.',
 'PROVIDER', 'DOCTOR', 'complication-profiles', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/complication-profiles"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-complication-profiles-nurse',
 'NURSE: view a published complication profile and its anticipated classes.',
 'PROVIDER', 'NURSE', 'complication-profiles', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/complication-profiles"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-complication-profiles-consultant',
 'CONSULTANT: view a published complication profile and its anticipated classes.',
 'PROVIDER', 'CONSULTANT', 'complication-profiles', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/complication-profiles"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-complication-profiles-ward-manager',
 'WARD_MANAGER: view a published complication profile and its anticipated classes.',
 'PROVIDER', 'WARD_MANAGER', 'complication-profiles', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/complication-profiles"}', true);
