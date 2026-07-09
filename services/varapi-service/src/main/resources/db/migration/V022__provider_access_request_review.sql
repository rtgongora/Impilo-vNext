-- =============================================================================
-- Varapi V022 — Provider access request review decisions (IATG Trust Console).
--
-- Adds the reviewer-decision columns to varapi.provider_access_request so the
-- governance console can record WHO decided a request, WHEN, and WHY. The
-- decision itself lands in the existing `status` column (APPROVED / REJECTED /
-- NEEDS_MORE_INFORMATION); these columns make the decision auditable without a
-- second truth table.
-- =============================================================================

ALTER TABLE varapi.provider_access_request
    ADD COLUMN IF NOT EXISTS decided_by    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS decided_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS decision_note VARCHAR(500);

COMMENT ON COLUMN varapi.provider_access_request.decided_by IS
    'Actor id of the reviewer who took the terminal/interim decision (Trust Console).';
COMMENT ON COLUMN varapi.provider_access_request.decided_at IS
    'When the review decision was recorded.';
COMMENT ON COLUMN varapi.provider_access_request.decision_note IS
    'Reviewer-supplied note explaining the decision.';
