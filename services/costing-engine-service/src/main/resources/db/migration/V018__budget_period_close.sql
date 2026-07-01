-- Intelligent Budgeting — period close (Wave 3). Links a budget to an existing
-- costa_financial_periods row and records the close/carry-forward decision.
CREATE TABLE IF NOT EXISTS costa_budget_period_close (
    id                    BIGSERIAL PRIMARY KEY,
    close_id              UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id             UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id             UUID NOT NULL,
    financial_period_id   UUID NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'SOFT_CLOSED',
    closed_by             VARCHAR(128),
    closed_at             TIMESTAMPTZ,
    carry_forward_amount  NUMERIC(18,2),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_budget_period_close UNIQUE (tenant_id, budget_id, financial_period_id),
    CONSTRAINT chk_costa_period_close_status
        CHECK (status IN ('OPEN','SOFT_CLOSED','HARD_CLOSED','REOPENED'))
);
CREATE INDEX idx_costa_period_close_budget ON costa_budget_period_close (tenant_id, budget_id);
