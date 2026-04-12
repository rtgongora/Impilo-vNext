-- Institutional Financial Reporting
CREATE TABLE IF NOT EXISTS costa_financial_periods (
    id              BIGSERIAL PRIMARY KEY,
    period_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    period_type     VARCHAR(16) NOT NULL,
    period_year     INT NOT NULL,
    period_month    INT,
    period_quarter  INT,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(16) DEFAULT 'OPEN',
    closed_by       VARCHAR(128),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_period UNIQUE (tenant_id, period_type, period_year, period_month)
);
CREATE TABLE IF NOT EXISTS costa_financial_summaries (
    id              BIGSERIAL PRIMARY KEY,
    summary_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID,
    period_id       UUID REFERENCES costa_financial_periods(period_id),
    summary_type    VARCHAR(32) NOT NULL,
    total_revenue   DECIMAL(14,2) DEFAULT 0,
    total_cost      DECIMAL(14,2) DEFAULT 0,
    total_receivable DECIMAL(14,2) DEFAULT 0,
    total_collected  DECIMAL(14,2) DEFAULT 0,
    total_writeoff  DECIMAL(14,2) DEFAULT 0,
    total_insurer_billed DECIMAL(14,2) DEFAULT 0,
    total_insurer_paid DECIMAL(14,2) DEFAULT 0,
    total_patient_billed DECIMAL(14,2) DEFAULT 0,
    total_patient_paid DECIMAL(14,2) DEFAULT 0,
    encounter_count  INT DEFAULT 0,
    metadata_json   JSONB DEFAULT '{}',
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_summaries_facility ON costa_financial_summaries (facility_id, tenant_id);
CREATE INDEX idx_costa_summaries_period ON costa_financial_summaries (period_id);
CREATE TABLE IF NOT EXISTS costa_budget_allocations (
    id              BIGSERIAL PRIMARY KEY,
    allocation_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    department_id   VARCHAR(128),
    budget_category VARCHAR(64) NOT NULL,
    period_year     INT NOT NULL,
    allocated_amount DECIMAL(14,2) NOT NULL,
    spent_amount    DECIMAL(14,2) DEFAULT 0,
    committed_amount DECIMAL(14,2) DEFAULT 0,
    available_amount DECIMAL(14,2),
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_budget_facility ON costa_budget_allocations (facility_id, period_year);
