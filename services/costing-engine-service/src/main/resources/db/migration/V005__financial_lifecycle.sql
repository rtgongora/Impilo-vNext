-- COSTA financial lifecycle: costing profiles, cost/charge records, billing accounts,
-- invoice enrichment, statements, payment allocations, and financial access audit.

-- Costing configuration
CREATE TABLE IF NOT EXISTS costa_costing_profiles (
    profile_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(64)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    costing_type    VARCHAR(40)     NOT NULL,
    scope_type      VARCHAR(40)     NOT NULL,
    scope_ref       VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_costa_costing_profile_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_costa_costing_profiles_tenant ON costa_costing_profiles (tenant_id, status);

-- Internal economic cost
CREATE TABLE IF NOT EXISTS costa_cost_records (
    cost_record_id  VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    cost_record_code VARCHAR(64)    NOT NULL,
    source_type     VARCHAR(64)     NOT NULL,
    source_ref      VARCHAR(128)    NOT NULL,
    client_ref      VARCHAR(128),
    provider_ref    VARCHAR(128),
    facility_id     UUID,
    item_ref        VARCHAR(128),
    service_ref     VARCHAR(128),
    cost_type       VARCHAR(40)     NOT NULL,
    cost_amount     NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    costing_profile_id VARCHAR(26)   REFERENCES costa_costing_profiles(profile_id),
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_costa_cost_records_tenant ON costa_cost_records (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_costa_cost_records_source ON costa_cost_records (source_type, source_ref);

-- Billable charge (may exist before a bill line is posted)
CREATE TABLE IF NOT EXISTS costa_charge_records (
    charge_id       VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    charge_code     VARCHAR(64)     NOT NULL,
    charge_type     VARCHAR(40)     NOT NULL,
    source_type     VARCHAR(64)     NOT NULL,
    source_ref      VARCHAR(128)    NOT NULL,
    client_ref      VARCHAR(128),
    payer_ref       VARCHAR(128),
    provider_ref    VARCHAR(128),
    facility_id     UUID,
    item_ref        VARCHAR(128),
    offering_ref    VARCHAR(128),
    service_ref     VARCHAR(128),
    bill_id         VARCHAR(26)     REFERENCES costa_bill_headers(bill_id),
    line_id         VARCHAR(26)     REFERENCES costa_bill_lines(line_id),
    charge_amount   NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    charge_status   VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    billable_flag   BOOLEAN         NOT NULL DEFAULT true,
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_costa_charge_records_tenant ON costa_charge_records (tenant_id, charge_status);
CREATE INDEX IF NOT EXISTS idx_costa_charge_records_bill ON costa_charge_records (bill_id);

-- Bill-to context (owner may be patient CPID, provider, org, etc.)
CREATE TABLE IF NOT EXISTS costa_billing_accounts (
    billing_account_id VARCHAR(26)  PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    account_type    VARCHAR(40)     NOT NULL,
    owner_ref       VARCHAR(128)    NOT NULL,
    payer_type      VARCHAR(40),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    balance_hint    NUMERIC(14,2),
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_costa_billing_account UNIQUE (tenant_id, account_type, owner_ref)
);
CREATE INDEX IF NOT EXISTS idx_costa_billing_accounts_owner ON costa_billing_accounts (tenant_id, owner_ref);

-- Invoice enrichment (additive)
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE costa_invoices i SET tenant_id = b.tenant_id FROM costa_bill_headers b WHERE i.bill_id = b.bill_id AND i.tenant_id IS NULL;
UPDATE costa_invoices SET tenant_id = gen_random_uuid() WHERE tenant_id IS NULL;
ALTER TABLE costa_invoices ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS billing_account_id VARCHAR(26) REFERENCES costa_billing_accounts(billing_account_id);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS client_ref VARCHAR(128);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS payer_ref VARCHAR(128);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS invoice_status VARCHAR(30) NOT NULL DEFAULT 'ISSUED';
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS subtotal NUMERIC(14,2);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS total NUMERIC(14,2);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS settled_amount NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS outstanding_amount NUMERIC(14,2);
ALTER TABLE costa_invoices ADD COLUMN IF NOT EXISTS metadata_json JSONB NOT NULL DEFAULT '{}';

CREATE TABLE IF NOT EXISTS costa_invoice_lines (
    line_id         VARCHAR(26)     PRIMARY KEY,
    invoice_id      VARCHAR(26)     NOT NULL REFERENCES costa_invoices(invoice_id) ON DELETE CASCADE,
    charge_id       VARCHAR(26)     REFERENCES costa_charge_records(charge_id),
    bill_line_id    VARCHAR(26)     REFERENCES costa_bill_lines(line_id),
    description     VARCHAR(500)    NOT NULL,
    quantity        NUMERIC(10,3)   NOT NULL DEFAULT 1,
    unit_price      NUMERIC(14,2)   NOT NULL,
    line_amount     NUMERIC(14,2)   NOT NULL,
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_costa_invoice_lines_inv ON costa_invoice_lines (invoice_id);

-- Periodic statements
CREATE TABLE IF NOT EXISTS costa_statements (
    statement_id    VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    statement_number VARCHAR(64)    NOT NULL,
    billing_account_id VARCHAR(26) REFERENCES costa_billing_accounts(billing_account_id),
    account_owner_ref VARCHAR(128) NOT NULL,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'GENERATED',
    total_due       NUMERIC(14,2)   NOT NULL DEFAULT 0,
    total_paid      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    generated_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    metadata_json   JSONB           NOT NULL DEFAULT '{}',
    CONSTRAINT uq_costa_statement_number UNIQUE (tenant_id, statement_number)
);
CREATE INDEX IF NOT EXISTS idx_costa_statements_account ON costa_statements (tenant_id, billing_account_id);

-- Links MusheX payment truth to COSTA receivable documents
CREATE TABLE IF NOT EXISTS costa_payment_allocations (
    id              BIGSERIAL       PRIMARY KEY,
    allocation_id   UUID            NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID            NOT NULL,
    invoice_id      VARCHAR(26)     NOT NULL REFERENCES costa_invoices(invoice_id),
    charge_id       VARCHAR(26)     REFERENCES costa_charge_records(charge_id),
    costa_payment_id BIGINT         NOT NULL REFERENCES costa_payments(id),
    mushex_payment_intent_id VARCHAR(100),
    allocated_amount NUMERIC(14,2)  NOT NULL,
    allocation_status VARCHAR(30)  NOT NULL DEFAULT 'SETTLED',
    allocated_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    metadata_json   JSONB           NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_costa_pay_alloc_invoice ON costa_payment_allocations (invoice_id);
CREATE INDEX IF NOT EXISTS idx_costa_pay_alloc_payment ON costa_payment_allocations (costa_payment_id);

-- COSTA-side financial access audit (complements platform audit)
CREATE TABLE IF NOT EXISTS costa_financial_access_audit (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(128),
    actor_type      VARCHAR(40),
    action          VARCHAR(128)    NOT NULL,
    target_type     VARCHAR(64)     NOT NULL,
    target_id       VARCHAR(128)    NOT NULL,
    outcome         VARCHAR(32)     NOT NULL,
    correlation_id  UUID,
    workflow_context JSONB          NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_costa_fin_audit_tenant ON costa_financial_access_audit (tenant_id, created_at DESC);
