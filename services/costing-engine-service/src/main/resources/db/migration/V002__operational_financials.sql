-- Provider Operational Financial Management
CREATE TABLE IF NOT EXISTS costa_revenue_entries (
    id              BIGSERIAL PRIMARY KEY,
    entry_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    department_id   VARCHAR(128),
    encounter_id    VARCHAR(128),
    bill_id         VARCHAR(128),
    revenue_type    VARCHAR(32) NOT NULL,
    service_code    VARCHAR(64),
    description     VARCHAR(512),
    gross_amount    DECIMAL(14,2) NOT NULL,
    discount_amount DECIMAL(14,2) DEFAULT 0,
    net_amount      DECIMAL(14,2) NOT NULL,
    payer_type      VARCHAR(32),
    payer_ref       VARCHAR(128),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    period_year     INT NOT NULL,
    period_month    INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_revenue_facility ON costa_revenue_entries (facility_id, tenant_id, period_year, period_month);
CREATE INDEX idx_costa_revenue_dept ON costa_revenue_entries (department_id, facility_id);
CREATE TABLE IF NOT EXISTS costa_receivables (
    id              BIGSERIAL PRIMARY KEY,
    receivable_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    debtor_type     VARCHAR(32) NOT NULL,
    debtor_ref      VARCHAR(128) NOT NULL,
    debtor_name     VARCHAR(255),
    bill_id         VARCHAR(128),
    invoice_id      VARCHAR(128),
    original_amount DECIMAL(14,2) NOT NULL,
    paid_amount     DECIMAL(14,2) DEFAULT 0,
    outstanding     DECIMAL(14,2) NOT NULL,
    currency        VARCHAR(3) DEFAULT 'ZWL',
    due_date        DATE NOT NULL,
    status          VARCHAR(16) DEFAULT 'OPEN',
    aging_bucket    VARCHAR(16),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_receivables_facility ON costa_receivables (facility_id, tenant_id, status);
CREATE INDEX idx_costa_receivables_debtor ON costa_receivables (debtor_ref, debtor_type);
CREATE INDEX idx_costa_receivables_aging ON costa_receivables (aging_bucket, status);
CREATE TABLE IF NOT EXISTS costa_daily_cashup (
    id              BIGSERIAL PRIMARY KEY,
    cashup_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    cashier_id      VARCHAR(128) NOT NULL,
    shift_id        VARCHAR(128),
    cashup_date     DATE NOT NULL,
    cash_collected   DECIMAL(14,2) DEFAULT 0,
    card_collected   DECIMAL(14,2) DEFAULT 0,
    mobile_collected DECIMAL(14,2) DEFAULT 0,
    insurance_billed DECIMAL(14,2) DEFAULT 0,
    total_collected  DECIMAL(14,2) DEFAULT 0,
    total_billed     DECIMAL(14,2) DEFAULT 0,
    variance         DECIMAL(14,2) DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'OPEN',
    closed_at       TIMESTAMPTZ,
    supervisor_id   VARCHAR(128),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_cashup_facility ON costa_daily_cashup (facility_id, cashup_date);
CREATE TABLE IF NOT EXISTS costa_cost_centers (
    id              BIGSERIAL PRIMARY KEY,
    center_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    code            VARCHAR(32) NOT NULL,
    parent_center_id UUID,
    center_type     VARCHAR(32) DEFAULT 'DEPARTMENT',
    budget_amount   DECIMAL(14,2),
    budget_period   VARCHAR(16) DEFAULT 'ANNUAL',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS costa_cost_center_entries (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    center_id       UUID NOT NULL REFERENCES costa_cost_centers(center_id),
    entry_type      VARCHAR(16) NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    description     VARCHAR(512),
    reference_type  VARCHAR(32),
    reference_id    VARCHAR(128),
    period_year     INT NOT NULL,
    period_month    INT NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_cc_entries ON costa_cost_center_entries (center_id, period_year, period_month);
CREATE TABLE IF NOT EXISTS costa_write_offs (
    id              BIGSERIAL PRIMARY KEY,
    writeoff_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    receivable_id   UUID REFERENCES costa_receivables(receivable_id),
    amount          DECIMAL(14,2) NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    approved_by     VARCHAR(128),
    approved_at     TIMESTAMPTZ,
    status          VARCHAR(16) DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
