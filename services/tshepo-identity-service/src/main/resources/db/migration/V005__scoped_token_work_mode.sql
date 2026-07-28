-- =============================================================================
-- tshepo-identity V005 — WorkMode claims on the work-context token (Phase B).
--
-- context_claims gains work_mode (the minted WorkMode name) and
-- clinical_data_access (derived from the mode, never caller-supplied — see
-- TokenIssuanceService.resolvePurposeOfUse / assertAnchorPresent). No schema
-- change needed for the JSONB column itself (V004); this migration documents
-- the new keys and adds the expression indexes the mode-scoped revocation
-- query (Phase C) and the "recent pattern" ranking read need.
-- =============================================================================

COMMENT ON COLUMN tshepo_identity.scoped_token.context_claims IS
    'WORK_CONTEXT only: facility_id/department_id/ward_id/programme_id/org_id/'
    'jurisdiction_code/assignment_id/workspace_id/service_point_id/context_id/'
    'context_kind/role_template_id/work_mode/clinical_data_access/purpose_of_use/'
    'session_assurance. Never live licence/scope truth.';

CREATE INDEX IF NOT EXISTS idx_scoped_token_work_mode
    ON tshepo_identity.scoped_token ((context_claims->>'work_mode'))
    WHERE status = 'ACTIVE' AND token_kind = 'WORK_CONTEXT';

CREATE INDEX IF NOT EXISTS idx_scoped_token_context_id
    ON tshepo_identity.scoped_token ((context_claims->>'context_id'))
    WHERE status = 'ACTIVE' AND token_kind = 'WORK_CONTEXT';
