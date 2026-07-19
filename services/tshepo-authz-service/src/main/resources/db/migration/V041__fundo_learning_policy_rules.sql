-- ============================================================================
-- V041 — Fundo (learning-service) ext_authz ALLOW rules
--
-- Before this migration the /internal/v1/learning/** BFF lane had NO matching
-- policy rule at all → PDP fail-closed (NO_MATCHING_RULES → DENY): the moment
-- real ext_authz is enabled on the gateway, every Fundo surface 403s for every
-- actor. (Masked today on the preview estate, whose envoy configmap is a bare
-- passthrough with no ext_authz filter.)
--
-- Matrix (mirrors the shell's LEARNING_AUTHOR role group and the learner
-- surfaces being open to any authenticated person — citizens take wellness /
-- public-health courses too; "help before identity" does not extend to
-- unauthenticated writes):
--   * READ  (GET  /internal/v1/learning/**)            → any authenticated actor
--   * LEARNER WRITES (self-enrolment lifecycle, progress, lesson-open,
--     assessment attempts, media watch-progress)       → any authenticated actor
--   * AUTHORING/ADMIN WRITES (everything else under the lane: catalog, modules,
--     lessons, assessments, pathways, media, library, studio, providers,
--     accreditation) → TRAINER / FACILITY_ADMIN / SYSTEM_ADMIN / DEVELOPER
--     (the LEARNING_AUTHOR group in ui/one-ui-shell/src/lib/auth/role-groups.ts)
--
-- ALLOW-only: no DENY rows (the PDP's DENY branch ignores `conditions`, so a
-- path_contains-scoped DENY would over-block — see the PolicyEngine register).
-- NULL columns are wildcards; every rule is pinned with path_contains so the
-- derived resource types (catalog, enrolments, …) cannot collide with other
-- services' same-named resources. DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Reads: every Fundo surface is readable by any authenticated actor ──
('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-read-any-authenticated',
 'Any authenticated actor: read all Fundo learning surfaces (catalogue, my-learning, enrolments, certificates, reports, studio reads).',
 NULL, NULL, NULL, 'GET', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/internal/v1/learning"}', true),

-- ── Learner writes: self-service learning lifecycle, any authenticated actor ──
('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-learner-enrolments',
 'Any authenticated actor: self-enrolment lifecycle (POST /learning/v11/enrolments, /{id}/start|cancel|certificate).',
 NULL, NULL, NULL, 'POST', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/learning/v11/enrolments"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-learner-progress',
 'Any authenticated actor: record own lesson progress (POST /learning/v11/progress).',
 NULL, NULL, NULL, 'POST', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/learning/v11/progress"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-learner-lesson-open',
 'Any authenticated actor: open a lesson (POST /learning/v11/lessons/{id}/open — resource derives to "open", authoring lesson-create derives to "lessons").',
 NULL, NULL, 'open', 'POST', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/internal/v1/learning"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-learner-attempts',
 'Any authenticated actor: submit assessment attempts (POST /learning/v11/assessments/{id}/attempts — resource derives to "attempts").',
 NULL, NULL, 'attempts', 'POST', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/internal/v1/learning"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-learner-watch-progress',
 'Any authenticated actor: report media watch progress (POST /learning/v11/media/{id}/watch-progress).',
 NULL, NULL, 'watch-progress', 'POST', NULL,
 false, false, 'ALLOW', 50, '{"path_contains": "/internal/v1/learning"}', true),

-- ── Authoring / admin writes: the LEARNING_AUTHOR role group ──
('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-author-trainer',
 'TRAINER: full Fundo authoring/admin writes (catalog, modules, lessons, assessments, pathways, media, library, studio).',
 NULL, 'TRAINER', NULL, NULL, NULL,
 false, false, 'ALLOW', 55, '{"path_contains": "/internal/v1/learning"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-author-facility-admin',
 'FACILITY_ADMIN: full Fundo authoring/admin writes.',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 55, '{"path_contains": "/internal/v1/learning"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-author-system-admin',
 'SYSTEM_ADMIN: full Fundo authoring/admin writes.',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL,
 false, false, 'ALLOW', 55, '{"path_contains": "/internal/v1/learning"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'fundo-author-developer',
 'DEVELOPER: full Fundo authoring/admin writes (platform development override, mirrors the shell LEARNING_AUTHOR group).',
 NULL, 'DEVELOPER', NULL, NULL, NULL,
 false, false, 'ALLOW', 55, '{"path_contains": "/internal/v1/learning"}', true);
