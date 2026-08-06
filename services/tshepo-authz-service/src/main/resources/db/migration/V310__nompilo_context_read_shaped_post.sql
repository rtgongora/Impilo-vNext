-- ============================================================================
-- V310 — /nompilo/context is a read-shaped POST
--
-- V309 shipped thirteen rules for the signed-in shell surface and twelve of them
-- fired. The thirteenth, shell-chrome-nompilo-context, did not: a browser session
-- with ext_authz enabled was still denied NO_ALLOW_RULE on /nompilo/context while
-- its structurally identical sibling shell-chrome-session-experience allowed.
--
-- The rule was never wrong about the path. It was wrong about the verb. V309 set
-- action='GET' on every rule and said so in its header — "no rule here grants a
-- write" — but the shell calls this endpoint with POST:
--
--     useNompilo.ts:65  apiClient.post("/internal/v1/nompilo/context", { … })
--
-- because the request carries a context body (current route, work mode, signals)
-- and receives guidance in return. It is a query whose parameters are too big for
-- a query string — a read-shaped POST — not a mutation.
--
-- Verified before granting the verb rather than assumed from the name:
-- NompiloGuidanceController.context() (@PostMapping("/context")) derives signals
-- and calls GuidanceServiceClient.resolveContext(). It persists nothing.
--
-- ── What this deliberately does NOT grant ───────────────────────────────────
-- The same controller exposes POSTs that DO mutate — /nompilo/dismiss and
-- /nompilo/follow-up record a person's decisions about guidance. They are
-- different paths, so this rule's path pin cannot reach them, and they are left
-- to be granted (or not) as the deliberate acts they are. V309's principle
-- survives intact: no rule in this pair grants a write. This one grants a read
-- that happens to travel by POST.
--
-- ── Why a second rule rather than editing V309's ────────────────────────────
-- An applied migration is history. V309 ran successfully in the preview estate;
-- rewriting its INSERT would leave the estate and the migration disagreeing, and
-- Flyway would not re-run it. The GET rule is left in place — harmless, and it
-- still covers the path should a GET variant ever be added.
--
-- Everything else follows V309: resource_type NULL with the path pinned (because
-- deriveResourceType would yield the uselessly generic "context"), role NULL
-- (SELF-scoped, and pinning to CITIZEN would blank a provider's shell), no
-- purpose (shell chrome has no purpose of use), no facility or workspace scope,
-- no LoA/AAL floor, priority 70.
--
-- The load-bearing safety fact is V309's and is restated because it applies here
-- too: ActorContextFilter in experience-bff overrides X-Actor-ID with the
-- health_id claim of the validated bearer JWT, so this endpoint is scoped to SELF
-- in the BFF rather than by this rule. IF THAT FILTER IS EVER REMOVED OR BYPASSED
-- THIS BECOMES A CROSS-PERSON READ.
--
-- Acceptance is not "the migration applied". It is a signed-in browser session
-- reaching /nompilo/context with ext_authz on and zero denials.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'shell-chrome-nompilo-context-post',
 'Assistant context for this session, requested by POST because the context travels in the body. Resolves guidance; persists nothing. Nompilo never overrides provider judgement.',
 NULL, NULL, NULL, 'POST', NULL, false, false, 'ALLOW', 70,
 '{"path_contains": "/nompilo/context"}'::jsonb, true);
