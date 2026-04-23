-- MusheX financial platform extensions: custodial wallets, cross-border remittance requests,
-- card profiles, reversals (distinct from refunds), and financial access audit.

CREATE TABLE IF NOT EXISTS mushex_wallet_accounts (
    wallet_id       VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    wallet_code     VARCHAR(64)     NOT NULL,
    owner_type      VARCHAR(40)     NOT NULL,
    owner_ref       VARCHAR(128)    NOT NULL,
    wallet_type     VARCHAR(40)     NOT NULL DEFAULT 'STANDARD',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    available_balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    ledger_balance  NUMERIC(14,2)   NOT NULL DEFAULT 0,
    external_wallet_ref VARCHAR(128),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_mushex_wallet_code UNIQUE (tenant_id, wallet_code)
);
CREATE INDEX IF NOT EXISTS idx_mushex_wallet_owner ON mushex_wallet_accounts (tenant_id, owner_type, owner_ref);

CREATE TABLE IF NOT EXISTS mushex_remittance_requests (
    remittance_request_id VARCHAR(26) PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    remittance_code VARCHAR(64)     NOT NULL,
    sender_ref      VARCHAR(128)    NOT NULL,
    recipient_ref   VARCHAR(128)    NOT NULL,
    recipient_type  VARCHAR(40)     NOT NULL,
    origin_country  VARCHAR(3),
    destination_country VARCHAR(3),
    domestic_or_international VARCHAR(16) NOT NULL DEFAULT 'DOMESTIC',
    remittance_purpose VARCHAR(64),
    health_context  JSONB,
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(30)     NOT NULL DEFAULT 'REQUESTED',
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_mushex_remittance_code UNIQUE (tenant_id, remittance_code)
);
CREATE INDEX IF NOT EXISTS idx_mushex_remittance_req_sender ON mushex_remittance_requests (tenant_id, sender_ref);

CREATE TABLE IF NOT EXISTS mushex_wallet_transactions (
    txn_id          VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    wallet_id       VARCHAR(26)     NOT NULL REFERENCES mushex_wallet_accounts(wallet_id),
    transaction_type VARCHAR(40)   NOT NULL,
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    direction       VARCHAR(8)      NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
    status          VARCHAR(20)     NOT NULL DEFAULT 'POSTED',
    reference_code  VARCHAR(64),
    related_payment_intent_id VARCHAR(26) REFERENCES mushex_payment_intents(intent_id),
    related_remittance_request_id VARCHAR(26) REFERENCES mushex_remittance_requests(remittance_request_id),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mushex_wallet_txn_wallet ON mushex_wallet_transactions (wallet_id, created_at DESC);

CREATE TABLE IF NOT EXISTS mushex_card_profiles (
    card_profile_id VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    wallet_id       VARCHAR(26)     REFERENCES mushex_wallet_accounts(wallet_id),
    owner_ref       VARCHAR(128),
    card_type       VARCHAR(40)     NOT NULL,
    card_status     VARCHAR(30)     NOT NULL DEFAULT 'ISSUED',
    token_ref       VARCHAR(200),
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expiry_meta     JSONB,
    metadata        JSONB
);
CREATE INDEX IF NOT EXISTS idx_mushex_card_wallet ON mushex_card_profiles (wallet_id);

CREATE TABLE IF NOT EXISTS mushex_reversal_records (
    reversal_id     VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    reversal_code   VARCHAR(64)     NOT NULL,
    original_txn_ref VARCHAR(128) NOT NULL,
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    reason          VARCHAR(500),
    reversal_status VARCHAR(30)   NOT NULL DEFAULT 'INITIATED',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    metadata        JSONB,
    CONSTRAINT uq_mushex_reversal_code UNIQUE (tenant_id, reversal_code)
);

CREATE TABLE IF NOT EXISTS mushex_financial_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    actor_id       VARCHAR(128),
    actor_type      VARCHAR(40),
    action          VARCHAR(128)    NOT NULL,
    target_type     VARCHAR(64)     NOT NULL,
    target_id       VARCHAR(128)    NOT NULL,
    outcome         VARCHAR(32)     NOT NULL,
    correlation_id  UUID,
    workflow_context JSONB          NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mushex_fin_audit_tenant ON mushex_financial_audit_log (tenant_id, created_at DESC);
