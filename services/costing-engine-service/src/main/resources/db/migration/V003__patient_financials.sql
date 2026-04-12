-- Patient Financial Management

CREATE TABLE IF NOT EXISTS costa_patient_accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    total_billed    DECIMAL(14,2) DEFAULT 0,
    total_paid      DECIMAL(14,2) DEFAULT 0,
    total_insurer   DECIMAL(14,2) DEFAULT 0,
    total_outstanding DECIMAL(14,2) DEFAULT 0,
    total_writeoff  DECIMAL(14,2) DEFAULT 0,
    currency        VARCHAR(3) DEFAULT 'ZWL',
    last_activity   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_patient_account UNIQUE (tenant_id, patient_cpid)
);

CREATE TABLE IF NOT EXISTS costa_patient_transactions (
    id              BIGSERIAL PRIMARY KEY,
    txn_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    account_id      UUID REFERENCES costa_patient_accounts(account_id),
    txn_type        VARCHAR(32) NOT NULL,
    facility_id     UUID,
    encounter_id    VARCHAR(128),
    bill_id         VARCHAR(128),
    description     VARCHAR(512),
    gross_amount    DECIMAL(14,2),
    insurer_amount  DECIMAL(14,2) DEFAULT 0,
    patient_amount  DECIMAL(14,2),
    payment_method  VARCHAR(32),
    payment_ref     VARCHAR(128),
    txn_date        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_costa_ptxn_patient ON costa_patient_transactions (patient_cpid, tenant_id);
CREATE INDEX IF NOT EXISTS idx_costa_ptxn_date ON costa_patient_transactions (txn_date);

CREATE TABLE IF NOT EXISTS costa_payment_plans (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    patient_cpid    VARCHAR(128) NOT NULL,
    total_amount    DECIMAL(14,2) NOT NULL,
    paid_amount     DECIMAL(14,2) DEFAULT 0,
    installment_amount DECIMAL(14,2) NOT NULL,
    frequency       VARCHAR(16) DEFAULT 'MONTHLY',
    next_due_date   DATE,
    installments_total INT NOT NULL,
    installments_paid INT DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS costa_financial_documents (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    doc_type        VARCHAR(32) NOT NULL,
    subject_type    VARCHAR(32) NOT NULL,
    subject_ref     VARCHAR(128) NOT NULL,
    recipient_type  VARCHAR(32),
    recipient_ref   VARCHAR(128),
    title           VARCHAR(255),
    content_json    JSONB NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until     DATE,
    status          VARCHAR(16) DEFAULT 'GENERATED'
);

CREATE INDEX IF NOT EXISTS idx_costa_findocs_subject ON costa_financial_documents (subject_ref, subject_type, tenant_id);
