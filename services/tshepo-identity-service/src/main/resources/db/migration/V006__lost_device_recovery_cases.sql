CREATE TABLE IF NOT EXISTS tshepo_identity.lost_device_recovery_case (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_user_id VARCHAR(255) NOT NULL,
    requester_user_id VARCHAR(255) NOT NULL,
    approver_user_id VARCHAR(255),
    status VARCHAR(24) NOT NULL,
    reason TEXT NOT NULL,
    evidence_json JSONB NOT NULL,
    selected_credential_ids JSONB NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    execution_reference VARCHAR(255),
    execution_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_recovery_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','EXECUTED','EXECUTION_FAILED')),
    CONSTRAINT ck_recovery_two_person CHECK (approver_user_id IS NULL OR approver_user_id <> requester_user_id)
);

CREATE INDEX IF NOT EXISTS idx_recovery_case_tenant_status
    ON tshepo_identity.lost_device_recovery_case (tenant_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_recovery_case_target
    ON tshepo_identity.lost_device_recovery_case (tenant_id, target_user_id, requested_at DESC);
