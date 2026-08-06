-- ============================================================================
-- V309 — Policy rules for the signed-in shell surface
--
-- With ext_authz enabled, a signed-in citizen session was denied on 17 shell
-- endpoints (2026-08-06, browser-verified, rolled back). Every denial was
-- NO_ALLOW_RULE: rules were found and evaluated, and none granted the request.
--
-- Not a bug, and not simply an absence either. Two things were true:
--
--   1. Most shell endpoints had no rule at all. The estate's 529 rules are
--      DOMAIN rules — clinical lanes, learning, the confidential lane — and
--      every citizen-matchable one was path-pinned somewhere else.
--
--   2. workspace-state DID have rules: seventeen of them, one per role
--      (shell-workspace-state-citizen, -nurse, -clinician, …), all active. They
--      never fired because each demands a PURPOSE the shell does not send for
--      that role. Eleven require OPERATIONS, three PUBLIC_HEALTH, three
--      TREATMENT — while the shell derives purpose-of-use from work mode
--      (admin/oversight → OPERATIONS, finance → PAYMENT, …), so a citizen with
--      no work mode never presents OPERATIONS and shell-workspace-state-citizen
--      cannot match.
--
-- Hence purpose NULL below, and it is a correction rather than a loosening:
-- "which workspace should I render" has no purpose of use. It is not treatment,
-- not operations, not payment. Requiring one is a category error, and the
-- seventeen rules are the evidence — they encode a purpose for an action that
-- has none, and so grant nothing.
--
-- Those seventeen are left in place. They are lower-priority (50–65) so they are
-- still consulted first and can still grant; this rule is the fallback that makes
-- chrome reachable when they do not. Deleting them is a separate governance act,
-- not a side effect of this migration.
--
-- ── The safety fact these rules rest on ─────────────────────────────────────
-- ActorContextFilter (experience-bff) overrides X-Actor-ID with the health_id
-- claim of the validated bearer JWT. Every endpoint below is therefore scoped to
-- SELF in the BFF, not by the rule, and none takes a subject as a query
-- parameter. IF THAT FILTER IS EVER REMOVED OR BYPASSED, EVERY RULE HERE BECOMES
-- A CROSS-PERSON READ. It is the load-bearing assumption and is stated here so
-- it cannot be lost.
--
-- ── Why path_contains and not resource_type ─────────────────────────────────
-- deriveResourceType takes the last non-UUID path segment, which on this surface
-- yields values as generic as "status", "context", "feed" and "groups". A rule on
-- resource_type='status' would grant every …/status endpoint in the estate.
-- Every rule below leaves resource_type NULL and pins the path — the same idiom
-- the existing seeds use, for the same reason.
--
-- ── Scope, floors and roles ─────────────────────────────────────────────────
-- action='GET' throughout: no rule here grants a write.
-- facility_scope/workspace_scope false: a person reading their own shell has no
--   facility context, and PolicyEngine refuses a facility-scoped rule when
--   facilityId is null.
-- role NULL: these are SELF-scoped reads, scoped by actor identity rather than
--   role. Pinning them to CITIZEN would blank a provider's shell.
-- No min_loa/min_aal floor. Measured: an ordinary login yields
--   acr=urn:impilo:aal1 → effective LoA 1, so a floor of 1 is satisfied by every
--   session by construction (decoration) and a floor of 2 denies ordinary
--   sign-in. aal2 IS reachable — the impilo-browser-step-up-v1 ladder works and
--   demands a second factor on request — but requiring it here would contradict
--   the estate's existing, deliberate boundary: Work sign-in requires aal2,
--   Personal does not. Shell chrome is personal. Graduated friction, as doctrine
--   describes. Raise Tier 2 deliberately later if that boundary moves.
--
-- priority 70 orders these AFTER the existing 40–60 lane rules, so a narrower
-- lane rule is still consulted first. It does not affect DENY precedence: rules
-- are ordered effect DESC before priority, so a DENY is evaluated first whatever
-- its number, and the specially-protected and work-mode boundary rules still
-- bite.
--
-- Verification is not "the migration applied". The acceptance test is a signed-in
-- browser session counting denials on these endpoints, with zero as the pass
-- condition — the check that caught both failed cutovers.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

-- ── Tier 1: session chrome. Carries no personal record data — this is the
--    shell drawing itself.
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-workspace-state',
 'Which workspace the shell should render for the signed-in person. No record data.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/shell/workspace-state"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-session-experience',
 'The session''s own experience/mode. Server-derived actor; no subject parameter.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/session/experience"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-nompilo-context',
 'Assistant context for this session. Nompilo never overrides provider judgement.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/nompilo/context"}'::jsonb, true),

-- ── Tier 2: the person's own record-adjacent reads. Personal data, safe only
--    because the actor is server-derived (see the header).
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-profile-visibility',
 'The person''s own visibility preferences.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/profile/visibility"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-assurance-status',
 'The person''s own identity-assurance level, so the shell can show where they stand.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/identity/assurance/status"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-affiliations',
 'The person''s own affiliations.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/identity/affiliations"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-linked-ids',
 'The person''s own linked identifiers.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/identity/linked-ids"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-notifications',
 'The person''s own inbox. Recipient scope is derived server-side (item 83), never from a query parameter.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/assistant/notifications"}'::jsonb, true),

-- Anchored to /internal/v1/appointments rather than a bare "/appointments":
-- path_contains is a substring test, and a bare pin is a standing trap for
-- whatever appointment lane is added next.
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-appointments',
 'The person''s own appointments.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/internal/v1/appointments"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-citizen-feed',
 'The person''s own feed (mobile lane).',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/mobile/citizen/feed"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-self-citizen-appointments',
 'The person''s own appointments (mobile lane).',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/mobile/citizen/appointments"}'::jsonb, true),

-- ── Tier 3: directory reads. Not personal data; the facility register is
--    already readable on the public lane.
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-directory-facilities',
 'The national facility register, as the shell lists it.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/internal/v1/facilities"}'::jsonb, true),

('00000000-0000-0000-0000-000000000001'::uuid, 'shell-directory-community-groups',
 'Community group directory.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/community/groups"}'::jsonb, true);
