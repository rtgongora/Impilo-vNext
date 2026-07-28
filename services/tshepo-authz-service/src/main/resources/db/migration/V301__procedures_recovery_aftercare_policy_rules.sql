-- ============================================================================
-- V301 — procedures-service ext_authz ALLOW rules for the P7 safety-pause/sedation routes and
-- the P9 recovery/aftercare routes (Wave P-R2, the reachability re-wire the plan's own rule
-- triggers after three backend waves — P7+P8+P9 — since Wave P-R / V300).
--
-- Same reasoning as V300, not repeated at length: procedures-service is the pipeline's
-- cross-cutting platform layer, reusable across every specialty, so the broad clinical role set
-- (CLINICIAN, DOCTOR, NURSE, CONSULTANT, WARD_MANAGER) applies again rather than a surgical-only
-- set. All six routes below are read-only (engine-not-store) — reference content, not a write to
-- a clinical record.
--
-- ══ ROUTE SHAPE — no workaround needed here, unlike catalogue-detail in V300 ══
-- SafetyPauseController and RecoveryAndAftercareController were both built with the
-- code-as-query-parameter shape from the START (P7, P9) — the trap V300's own header describes
-- for catalogue-detail was avoided by construction, not fixed after the fact. Every path below
-- therefore has a FIXED final segment (deriveResourceType's answer never varies by which
-- template/level/setting code is requested), so resource_type is stable and no per-code
-- workaround is required, matching the pattern V300 already used for /procedures/competence.
--
-- ══ PATH-PIN CORRECTNESS ══ Every pin omits the trailing slash, for the same reason V300's
-- header explains at length (pathContainsSegment requires '/' or end-of-string immediately
-- after a match; a trailing-slash pin never matches a path with anything after it).
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Safety-pause templates — GET /internal/v1/procedures/safety-pause-templates?code=... (P7) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-safety-pause-templates-clinician',
 'CLINICIAN: view a safety-pause template and its confirmation items.',
 'PROVIDER', 'CLINICIAN', 'safety-pause-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/safety-pause-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-safety-pause-templates-doctor',
 'DOCTOR: view a safety-pause template and its confirmation items.',
 'PROVIDER', 'DOCTOR', 'safety-pause-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/safety-pause-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-safety-pause-templates-nurse',
 'NURSE: view a safety-pause template and its confirmation items.',
 'PROVIDER', 'NURSE', 'safety-pause-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/safety-pause-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-safety-pause-templates-consultant',
 'CONSULTANT: view a safety-pause template and its confirmation items.',
 'PROVIDER', 'CONSULTANT', 'safety-pause-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/safety-pause-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-safety-pause-templates-ward-manager',
 'WARD_MANAGER: view a safety-pause template and its confirmation items.',
 'PROVIDER', 'WARD_MANAGER', 'safety-pause-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/safety-pause-templates"}', true),

-- ── Sedation levels list — GET /internal/v1/procedures/sedation-levels (P7) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-levels-clinician',
 'CLINICIAN: view the sedation continuum.',
 'PROVIDER', 'CLINICIAN', 'sedation-levels', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-levels"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-levels-doctor',
 'DOCTOR: view the sedation continuum.',
 'PROVIDER', 'DOCTOR', 'sedation-levels', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-levels"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-levels-nurse',
 'NURSE: view the sedation continuum.',
 'PROVIDER', 'NURSE', 'sedation-levels', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-levels"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-levels-consultant',
 'CONSULTANT: view the sedation continuum.',
 'PROVIDER', 'CONSULTANT', 'sedation-levels', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-levels"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-levels-ward-manager',
 'WARD_MANAGER: view the sedation continuum.',
 'PROVIDER', 'WARD_MANAGER', 'sedation-levels', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-levels"}', true),

-- ── Sedation level detail — GET /internal/v1/procedures/sedation-level-detail?code=... (P7) ──
-- Distinct resource_type because it is a separate fixed path segment from "sedation-levels",
-- not because access should differ — the role set is identical, same rationale as
-- catalogue vs catalogue-detail in V300.
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-level-detail-clinician',
 'CLINICIAN: view one sedation level, including its resolved rescue capability.',
 'PROVIDER', 'CLINICIAN', 'sedation-level-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-level-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-level-detail-doctor',
 'DOCTOR: view one sedation level, including its resolved rescue capability.',
 'PROVIDER', 'DOCTOR', 'sedation-level-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-level-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-level-detail-nurse',
 'NURSE: view one sedation level, including its resolved rescue capability.',
 'PROVIDER', 'NURSE', 'sedation-level-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-level-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-level-detail-consultant',
 'CONSULTANT: view one sedation level, including its resolved rescue capability.',
 'PROVIDER', 'CONSULTANT', 'sedation-level-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-level-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-sedation-level-detail-ward-manager',
 'WARD_MANAGER: view one sedation level, including its resolved rescue capability.',
 'PROVIDER', 'WARD_MANAGER', 'sedation-level-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/sedation-level-detail"}', true),

-- ── Recovery settings list — GET /internal/v1/procedures/recovery-settings (P9) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-settings-clinician',
 'CLINICIAN: view recovery settings.',
 'PROVIDER', 'CLINICIAN', 'recovery-settings', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-settings"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-settings-doctor',
 'DOCTOR: view recovery settings.',
 'PROVIDER', 'DOCTOR', 'recovery-settings', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-settings"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-settings-nurse',
 'NURSE: view recovery settings.',
 'PROVIDER', 'NURSE', 'recovery-settings', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-settings"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-settings-consultant',
 'CONSULTANT: view recovery settings.',
 'PROVIDER', 'CONSULTANT', 'recovery-settings', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-settings"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-settings-ward-manager',
 'WARD_MANAGER: view recovery settings (bed/bay planning).',
 'PROVIDER', 'WARD_MANAGER', 'recovery-settings', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-settings"}', true),

-- ── Recovery setting detail — GET /internal/v1/procedures/recovery-setting-detail?code=... (P9) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-setting-detail-clinician',
 'CLINICIAN: view one recovery setting''s discharge-readiness criteria.',
 'PROVIDER', 'CLINICIAN', 'recovery-setting-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-setting-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-setting-detail-doctor',
 'DOCTOR: view one recovery setting''s discharge-readiness criteria.',
 'PROVIDER', 'DOCTOR', 'recovery-setting-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-setting-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-setting-detail-nurse',
 'NURSE: view one recovery setting''s discharge-readiness criteria.',
 'PROVIDER', 'NURSE', 'recovery-setting-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-setting-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-setting-detail-consultant',
 'CONSULTANT: view one recovery setting''s discharge-readiness criteria.',
 'PROVIDER', 'CONSULTANT', 'recovery-setting-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-setting-detail"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-recovery-setting-detail-ward-manager',
 'WARD_MANAGER: view one recovery setting''s discharge-readiness criteria.',
 'PROVIDER', 'WARD_MANAGER', 'recovery-setting-detail', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/recovery-setting-detail"}', true),

-- ── Aftercare templates — GET /internal/v1/procedures/aftercare-templates?code=... (P9) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-aftercare-templates-clinician',
 'CLINICIAN: view an aftercare template and its instructions.',
 'PROVIDER', 'CLINICIAN', 'aftercare-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/aftercare-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-aftercare-templates-doctor',
 'DOCTOR: view an aftercare template and its instructions.',
 'PROVIDER', 'DOCTOR', 'aftercare-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/aftercare-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-aftercare-templates-nurse',
 'NURSE: view an aftercare template and its instructions.',
 'PROVIDER', 'NURSE', 'aftercare-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/aftercare-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-aftercare-templates-consultant',
 'CONSULTANT: view an aftercare template and its instructions.',
 'PROVIDER', 'CONSULTANT', 'aftercare-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/aftercare-templates"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'procedures-aftercare-templates-ward-manager',
 'WARD_MANAGER: view an aftercare template and its instructions.',
 'PROVIDER', 'WARD_MANAGER', 'aftercare-templates', 'GET', 'TREATMENT',
 true, false, 'ALLOW', 50, '{"path_contains": "/procedures/aftercare-templates"}', true);
