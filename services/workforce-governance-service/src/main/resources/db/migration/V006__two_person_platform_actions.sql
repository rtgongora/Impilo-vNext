-- ============================================================================
-- workforce-governance-service V006 — Two-person platform action approvals
-- (IATG Wave-1, WS-A).
--
-- Records individual approver decisions against an access request whose
-- approvals_required JSON is non-empty (notably requestType PLATFORM_ACTION).
-- The UNIQUE (access_request_id, approver_user_id) constraint guarantees each
-- approver can decide at most once, so N distinct approvers are provably
-- distinct people. The initiator-cannot-approve rule is enforced in
-- TwoPersonApprovalService. Existing single-approver requests (null/empty
-- approvals_required) are unaffected.
-- ============================================================================

CREATE TABLE wgv_platform_action_approval (
    id                UUID PRIMARY KEY,
    access_request_id UUID NOT NULL REFERENCES wgv_access_request (id),
    approver_user_id  VARCHAR(255) NOT NULL,
    approver_role     VARCHAR(128),
    decision          VARCHAR(32) NOT NULL DEFAULT 'APPROVE',
                                        -- APPROVE|REJECT
    decided_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note              TEXT,
    CONSTRAINT uq_wgv_platform_action_approver UNIQUE (access_request_id, approver_user_id)
);

CREATE INDEX idx_wgv_paa_request ON wgv_platform_action_approval (access_request_id);
