-- =============================================================================
-- tshepo-identity V004 — Work-context scoped tokens (Provider+Place program W1/W3, D-P3)
--
-- Extends the scoped-token primitive so a provider's daily work session can be
-- carried by a short-lived, revocable token bound to a facility/workspace
-- context. token_kind distinguishes the existing S2S tokens (SERVICE) from the
-- new duty-scoped work tokens (WORK_CONTEXT). context_claims carries the
-- stable-for-the-session context (facility/department/workspace/role template/
-- provider public id/purpose/session assurance); live professional scope,
-- supervision and registry status stay PDP-resolved per action — never in-token.
-- =============================================================================

ALTER TABLE tshepo_identity.scoped_token
    ADD COLUMN IF NOT EXISTS token_kind      VARCHAR(32) NOT NULL DEFAULT 'SERVICE',
    ADD COLUMN IF NOT EXISTS context_claims  JSONB;

COMMENT ON COLUMN tshepo_identity.scoped_token.token_kind IS
    'SERVICE (S2S scoped token) | WORK_CONTEXT (provider duty-scoped work token, D-P3).';
COMMENT ON COLUMN tshepo_identity.scoped_token.context_claims IS
    'WORK_CONTEXT only: facility_id/department_id/workspace_id/role_template_id/provider_public_id/purpose_of_use/session_assurance. Never live licence/scope truth.';

-- Revocation teardown path: revoke-all-for-actor on provider privilege loss.
CREATE INDEX IF NOT EXISTS idx_scoped_token_actor_active
    ON tshepo_identity.scoped_token (tenant_id, actor_id)
    WHERE status = 'ACTIVE';
