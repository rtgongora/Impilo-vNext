-- Add utilization management fields to preauth
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS quantity_requested DECIMAL(10,2);
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS quantity_approved DECIMAL(10,2);
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS quantity_consumed DECIMAL(10,2) DEFAULT 0;
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS annual_limit DECIMAL(10,2);
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS approval_conditions JSONB DEFAULT '{}'::jsonb;
ALTER TABLE cv_preauth_requests ADD COLUMN IF NOT EXISTS utilization_period VARCHAR(16) DEFAULT 'ANNUAL';

-- Utilization summary view per member per benefit
CREATE TABLE IF NOT EXISTS cv_utilization_summary (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    member_cpid     VARCHAR(128) NOT NULL,
    plan_code       VARCHAR(64) NOT NULL,
    benefit_code    VARCHAR(64) NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    limit_amount    DECIMAL(10,2),
    used_amount     DECIMAL(10,2) DEFAULT 0,
    used_count      INT DEFAULT 0,
    last_updated    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_utilization UNIQUE (tenant_id, member_cpid, plan_code, benefit_code, period_start)
);

CREATE INDEX idx_cv_utilization_member ON cv_utilization_summary (member_cpid, tenant_id);
