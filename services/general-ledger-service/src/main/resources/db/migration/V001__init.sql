CREATE SCHEMA IF NOT EXISTS gl;

CREATE TABLE gl.accounts (
    account_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    account_code        VARCHAR(20) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    account_type        VARCHAR(32) NOT NULL,
    parent_account_id   UUID REFERENCES gl.accounts(account_id),
    currency            VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    is_system           BOOLEAN NOT NULL DEFAULT FALSE,
    normal_balance      VARCHAR(8) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gl_accounts_tenant_code UNIQUE (tenant_id, account_code),
    CONSTRAINT chk_gl_accounts_type CHECK (account_type IN (
        'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE', 'CONTRA'
    )),
    CONSTRAINT chk_gl_accounts_normal CHECK (normal_balance IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_gl_accounts_tenant ON gl.accounts (tenant_id);
CREATE INDEX idx_gl_accounts_parent ON gl.accounts (parent_account_id);

CREATE TABLE gl.fiscal_periods (
    period_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    fiscal_year     INT NOT NULL,
    period_number   INT NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    closed_by       VARCHAR(128),
    closed_at       TIMESTAMPTZ,
    CONSTRAINT uq_gl_fiscal_period UNIQUE (tenant_id, fiscal_year, period_number),
    CONSTRAINT chk_gl_fiscal_status CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED'))
);

CREATE INDEX idx_gl_fiscal_tenant_year ON gl.fiscal_periods (tenant_id, fiscal_year);

CREATE TABLE gl.journal_entries (
    entry_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    entry_number      VARCHAR(32) NOT NULL,
    entry_date        DATE NOT NULL,
    fiscal_period_id  UUID NOT NULL REFERENCES gl.fiscal_periods(period_id),
    description       VARCHAR(512),
    source_module     VARCHAR(32) NOT NULL,
    source_ref        VARCHAR(128),
    status            VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    posted_by         VARCHAR(128),
    posted_at         TIMESTAMPTZ,
    reversed_by       VARCHAR(128),
    reversed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gl_journal_entry_number UNIQUE (tenant_id, entry_number),
    CONSTRAINT chk_gl_journal_source CHECK (source_module IN (
        'COSTA', 'MUSHEX', 'PAYROLL', 'PROCUREMENT', 'INVENTORY', 'MANUAL'
    )),
    CONSTRAINT chk_gl_journal_status CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED'))
);

CREATE INDEX idx_gl_journal_tenant_period ON gl.journal_entries (tenant_id, fiscal_period_id);
CREATE INDEX idx_gl_journal_status ON gl.journal_entries (tenant_id, status);

CREATE TABLE gl.journal_lines (
    line_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id        UUID NOT NULL REFERENCES gl.journal_entries(entry_id) ON DELETE CASCADE,
    account_id      UUID NOT NULL REFERENCES gl.accounts(account_id),
    debit_amount    DECIMAL(14,2) NOT NULL DEFAULT 0,
    credit_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
    description     VARCHAR(512),
    cost_center_id  VARCHAR(128),
    department_id   VARCHAR(128),
    facility_id     UUID,
    project_code    VARCHAR(64)
);

CREATE INDEX idx_gl_journal_lines_entry ON gl.journal_lines (entry_id);
CREATE INDEX idx_gl_journal_lines_account ON gl.journal_lines (account_id);

CREATE TABLE gl.trial_balance_snapshots (
    snapshot_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    fiscal_period_id  UUID NOT NULL REFERENCES gl.fiscal_periods(period_id),
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    snapshot_json     JSONB NOT NULL
);

CREATE INDEX idx_gl_tb_snapshots_period ON gl.trial_balance_snapshots (fiscal_period_id);

CREATE TABLE gl.budget_lines (
    budget_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    account_id      UUID NOT NULL REFERENCES gl.accounts(account_id),
    fiscal_year     INT NOT NULL,
    budget_amount   DECIMAL(14,2) NOT NULL,
    actual_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
    variance        DECIMAL(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_gl_budget_line UNIQUE (tenant_id, account_id, fiscal_year)
);

CREATE TABLE gl.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'general-ledger-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_gl_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_gl_outbox_unpublished ON gl.event_outbox (created_at) WHERE published_at IS NULL;
