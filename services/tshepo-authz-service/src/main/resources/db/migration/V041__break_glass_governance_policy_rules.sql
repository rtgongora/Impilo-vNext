-- ============================================================================
-- V041 — BREAK-GLASS emergency-access governance policy rules (CZO-LEAD)
-- Gateway (ext_authz) rules for the break-glass MANAGEMENT surface exposed by
-- tshepo-authz-service (BreakGlassController, /v1/break-glass/**):
--   POST /v1/break-glass            — raise an emergency-access request (reason)
--   GET  /v1/break-glass/review     — list PENDING_REVIEW requests (supervisor)
--   POST /v1/break-glass/review/{id}— approve / reject a request (supervisor)
-- Next free after V040 (place-governance-journey).
--
-- These rules govern WHO may operate the break-glass lifecycle. The emergency
-- ACCESS decision itself (purpose=BREAK_GLASS on the data request) is decided in
-- the PolicyEngine Java path (Step 3 + the Step-4.5 break-glass doctrine guard:
-- verified-provider capacity + facility context + named patient + reason +
-- step-up), NOT by a DB rule — a BREAK_GLASS-purpose request short-circuits before
-- Step-4 rule matching. This migration is the governance perimeter around the
-- request/review endpoints.
--
-- Doctrine encoded here (identity-journey break-glass):
--   * Break-glass is RAISED only by an authenticated, sufficiently-verified
--     PROVIDER (min_loa>=2). "Never transform an unknown user into a health
--     worker" is enforced by FAIL-CLOSED DEFAULT-DENY: no ALLOW rule below grants
--     a citizen/anonymous actor the break-glass surface, so they get NO_ALLOW_RULE
--     — and the Java guard re-enforces verified-provider on the actual access.
--   * Retrospective review is a SUPERVISOR function (DISTRICT_SUPERVISOR /
--     CLINICAL_SUPERVISOR / FACILITY_ADMIN) — the requester never self-reviews.
--
-- WHY NO EXPLICIT `DENY citizen` ROW: the PolicyEngine evaluates a DENY rule on
-- (actor_type, role, action, purpose) ONLY and short-circuits BEFORE the JSONB
-- `conditions` (so a DENY's `path_contains` pin is IGNORED). A path-scoped citizen
-- DENY is therefore impossible without a resource_type-scoped DENY, and the
-- break-glass review segment collapses to the GENERIC resource_type `review`
-- (collides across services) — a `resource_type='review'` DENY would deny citizens
-- on every service's `/review` endpoint. Default-deny already covers this cleanly,
-- so we intentionally rely on it rather than ship an over-broad DENY.
--
-- COLLISION SAFETY: the PDP derives resource_type from the last path segment —
-- `break-glass` for the create, the generic word `review` for the review
-- endpoints. Each ALLOW rule pins `path_contains` to the break-glass route;
-- because ALLOW rules DO evaluate conditions, a same-segment `review` endpoint in
-- another service is NOT granted here. Effect DESC + priority ASC + first-matching
-- ALLOW semantics unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ── 1. Verified provider may RAISE a break-glass request (reason captured; min_loa>=2) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-request-provider',
 'PROVIDER (verified, LOA2+): raise a break-glass emergency-access request with a mandatory reason.',
 'PROVIDER', NULL, 'break-glass', 'POST', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass", "min_loa": 2}', true),

-- ── 2. Supervisors LIST the pending queue (retrospective review; requester never self-reviews) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-list-supervisor',
 'DISTRICT_SUPERVISOR: list PENDING_REVIEW break-glass requests for retrospective review.',
 'PROVIDER', 'DISTRICT_SUPERVISOR', 'review', 'GET', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-list-clinical-supervisor',
 'CLINICAL_SUPERVISOR: list PENDING_REVIEW break-glass requests for retrospective review.',
 'PROVIDER', 'CLINICAL_SUPERVISOR', 'review', 'GET', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-list-facility-admin',
 'FACILITY_ADMIN: list PENDING_REVIEW break-glass requests for the facility.',
 'STAFF', 'FACILITY_ADMIN', 'review', 'GET', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review", "min_loa": 2}', true),

-- ── 3. Supervisors DECIDE a review (approve / reject) — segment {id} collapses to `review`; pinned ──
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-decide-supervisor',
 'DISTRICT_SUPERVISOR: approve or reject a break-glass request.',
 'PROVIDER', 'DISTRICT_SUPERVISOR', 'review', 'POST', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review/", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-decide-clinical-supervisor',
 'CLINICAL_SUPERVISOR: approve or reject a break-glass request.',
 'PROVIDER', 'CLINICAL_SUPERVISOR', 'review', 'POST', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review/", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'break-glass-review-decide-facility-admin',
 'FACILITY_ADMIN: approve or reject a break-glass request for the facility.',
 'STAFF', 'FACILITY_ADMIN', 'review', 'POST', NULL, false, false, 'ALLOW', 40,
 '{"path_contains": "/v1/break-glass/review/", "min_loa": 2}', true);
