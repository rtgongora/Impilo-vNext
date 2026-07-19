-- ============================================================================
-- V039 — Provider+Place identity program W6: registration + verification pins
--
-- * /facility-registrations: submission is a citizen/operator action (any
--   authenticated actor may register — the anti-enum + steward review live
--   in-service); the steward queue on the same family is gated IN-SERVICE by
--   actor type, so one path-pinned ALLOW is safe.
-- * /verification-cases: opening is an operator action; deciding + listing are
--   gated in-service to verifier actor types (PLACE-VERIFY-REVIEW spec).
-- DENY-wins + first-matching-ALLOW unchanged.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'facility-registration-lane',
 'Authenticated actors may submit facility registrations (tuso /facility-registrations; FJ2 D-L5). Silent-dup matching + generic receipts + the in-service steward gate on the review queue carry the real control.',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "/facility-registrations"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'facility-verification-lane',
 'Authenticated actors may open proof-of-place verification cases; decisions and listings are verifier-gated in-service (tuso /verification-cases; FJ3/FJ4 D-L6, PLACE-VERIFY-REVIEW).',
 NULL, NULL, NULL, NULL, NULL,
 false, false, 'ALLOW', 60, '{"path_contains": "/verification-cases"}', true);
