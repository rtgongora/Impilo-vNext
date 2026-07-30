-- =====================================================================================
-- TSHEPO-authz :: V060 — the D7 cutover's first-flip instrument (Phase D, D7).
--
-- Two rows for /internal/v1/gateway-probe:
--
--   1. ALLOW, active=true  — so the route is reachable once the gateway's ext_authz filter
--      is on. Without it the PDP would refuse with NO_MATCHING_RULES, and a 403 would mean
--      "nothing matched" rather than "the PDP is on this path and can refuse it".
--   2. DENY,  active=false — the instrument. Activating it turns the route's 200 into a 403
--      and changes nothing else. Deactivating reverses it: one UPDATE, no redeploy, no
--      values change, no helm release.
--
-- Turning on the gateway's ext_authz filter raises one question that is easy to answer
-- wrongly: is the PDP actually on the request path? A 200 does not answer it, because a
-- request that never reaches the PDP also returns 200 — which is precisely the state the
-- preview estate has been in while the filter was gated off. Decision logs show the PDP is
-- being called by something, not that it can refuse this path.
--
-- The route is served but carries no data (see GatewayProbeController), so denying it costs
-- nothing. That is the whole design: an instrument whose reading is unambiguous because
-- nothing else depends on it.
--
-- No mode/workflow conditions on either row. The point is to test the transport, not the
-- condition evaluator — V043's authz-dim-demo rows already cover conditions, on paths no
-- service serves, which is why they cannot answer this question.
--
-- Pin has no trailing slash: pathContainsSegment is segment-bounded, so "/gateway-probe/"
-- would match only the bare collection root. See docs/architecture/POLICY_PATH_PIN_SEMANTICS.md.
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

('00000000-0000-0000-0000-000000000001'::uuid, 'd7-gateway-probe-allow',
 'D7 cutover instrument: permits GET /internal/v1/gateway-probe so the route is reachable '
 || 'once ext_authz is on. Paired with d7-gateway-probe-deny (SHADOW). Never depended on.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'ALLOW', 100,
 '{"path_contains": "/internal/v1/gateway-probe"}',
 true),

('00000000-0000-0000-0000-000000000001'::uuid, 'd7-gateway-probe-deny',
 'D7 cutover instrument: denies GET /internal/v1/gateway-probe to prove the gateway consults '
 || 'the PDP. Activate for the flip-1 check, deactivate immediately after. Never depended on.',
 NULL, NULL, NULL, 'GET', NULL, false, false, 'DENY', 10,
 '{"path_contains": "/internal/v1/gateway-probe"}',
 false)

ON CONFLICT DO NOTHING;
