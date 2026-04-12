-- =============================================================================
-- Mushe Wallet — Flyway V001: Core wallet, merchant accounts, transactions,
-- funding channels, smart card lifecycle, IPS card data
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS mushe;

-- ── Wallet Accounts ──────────────────────────────────────────────────────

CREATE TABLE mushe.wallets (
    id                  BIGSERIAL PRIMARY KEY,
    wallet_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    owner_type          VARCHAR(32) NOT NULL,  -- INDIVIDUAL, MERCHANT, INSTITUTIONAL
    owner_ref           VARCHAR(128) NOT NULL, -- CPID for individuals, Provider Number for merchants
    display_name        VARCHAR(255),
    currency            VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    balance             DECIMAL(14,2) NOT NULL DEFAULT 0,
    available_balance   DECIMAL(14,2) NOT NULL DEFAULT 0, -- balance minus holds
    held_balance        DECIMAL(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    kyc_level           VARCHAR(16) DEFAULT 'BASIC', -- BASIC, STANDARD, ENHANCED
    daily_limit         DECIMAL(14,2) DEFAULT 50000,
    monthly_limit       DECIMAL(14,2) DEFAULT 500000,
    pin_hash            VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mushe_wallet_owner UNIQUE (tenant_id, owner_type, owner_ref)
);
CREATE INDEX idx_mushe_wallets_owner ON mushe.wallets (owner_ref, owner_type, tenant_id);

-- ── Merchant Accounts (Providers as Merchants) ──────────────────────────

CREATE TABLE mushe.merchant_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    merchant_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    provider_number     VARCHAR(128) NOT NULL, -- Provider Number = Merchant ID
    facility_id         UUID,
    merchant_name       VARCHAR(255) NOT NULL,
    merchant_type       VARCHAR(32) NOT NULL DEFAULT 'HEALTH_PROVIDER', -- HEALTH_PROVIDER, PHARMACY, LAB, IMAGING
    settlement_account  VARCHAR(128), -- bank account for withdrawals
    settlement_bank     VARCHAR(64),
    settlement_branch   VARCHAR(64),
    mcc_code            VARCHAR(8) DEFAULT '8011', -- Merchant Category Code (healthcare)
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mushe_merchant_provider UNIQUE (tenant_id, provider_number)
);
CREATE INDEX idx_mushe_merchants_wallet ON mushe.merchant_accounts (wallet_id);

-- ── Transactions (Immutable Ledger) ─────────────────────────────────────

CREATE TABLE mushe.transactions (
    id                  BIGSERIAL PRIMARY KEY,
    txn_id              UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    txn_type            VARCHAR(32) NOT NULL,
    direction           VARCHAR(8) NOT NULL, -- CREDIT, DEBIT
    amount              DECIMAL(14,2) NOT NULL,
    fee                 DECIMAL(14,2) DEFAULT 0,
    net_amount          DECIMAL(14,2) NOT NULL,
    currency            VARCHAR(3) DEFAULT 'ZWL',
    balance_after       DECIMAL(14,2) NOT NULL,
    counterparty_type   VARCHAR(32),
    counterparty_ref    VARCHAR(128),
    counterparty_name   VARCHAR(255),
    reference           VARCHAR(255),
    description         VARCHAR(512),
    channel             VARCHAR(32), -- APP, USSD, CARD, POS, BANK_TRANSFER, MOBILE_MONEY
    status              VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key     VARCHAR(255),
    correlation_id      UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mushe_txn_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_mushe_txn_wallet ON mushe.transactions (wallet_id, created_at DESC);
CREATE INDEX idx_mushe_txn_ref ON mushe.transactions (reference);

-- ── Holds (Pending Authorizations) ──────────────────────────────────────

CREATE TABLE mushe.holds (
    id                  BIGSERIAL PRIMARY KEY,
    hold_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    amount              DECIMAL(14,2) NOT NULL,
    reason              VARCHAR(255),
    reference           VARCHAR(255),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CAPTURED, RELEASED, EXPIRED
    expires_at          TIMESTAMPTZ NOT NULL,
    captured_at         TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_mushe_holds_wallet ON mushe.holds (wallet_id, status);

-- ── Funding Channels ────────────────────────────────────────────────────

CREATE TABLE mushe.funding_sources (
    id                  BIGSERIAL PRIMARY KEY,
    source_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    source_type         VARCHAR(32) NOT NULL, -- BANK_ACCOUNT, MOBILE_MONEY, REMITTANCE
    provider            VARCHAR(64), -- EcoCash, InnBucks, OneMoney, CBZ, Stanbic, etc.
    account_ref         VARCHAR(128),
    account_name        VARCHAR(255),
    verified            BOOLEAN DEFAULT FALSE,
    status              VARCHAR(16) DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Smart Card Lifecycle ────────────────────────────────────────────────

CREATE TABLE mushe.cards (
    id                  BIGSERIAL PRIMARY KEY,
    card_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    card_number         VARCHAR(19) NOT NULL UNIQUE, -- PAN (masked in queries)
    card_type           VARCHAR(16) NOT NULL DEFAULT 'DUAL', -- DUAL (health + payment), PAYMENT_ONLY, HEALTH_ONLY
    card_network        VARCHAR(16) DEFAULT 'ZIMSWITCH', -- VISA, ZIMSWITCH, DUAL_NETWORK
    expiry_month        INT NOT NULL,
    expiry_year         INT NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ISSUED', -- ISSUED, ACTIVE, BLOCKED, EXPIRED, REPLACED
    pin_tries_remaining INT DEFAULT 3,
    health_applet_version VARCHAR(16),
    health_data_updated_at TIMESTAMPTZ,
    ips_version         VARCHAR(16), -- version of IPS data on card
    ips_updated_at      TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    blocked_at          TIMESTAMPTZ,
    blocked_reason      VARCHAR(255),
    replaced_by         UUID,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_mushe_cards_wallet ON mushe.cards (wallet_id);

-- ── Card Health Data (encrypted IPS + summary stored off-card for sync) ─

CREATE TABLE mushe.card_health_data (
    id                  BIGSERIAL PRIMARY KEY,
    card_id             UUID NOT NULL REFERENCES mushe.cards(card_id),
    tenant_id           UUID NOT NULL,
    patient_cpid        VARCHAR(128) NOT NULL,
    data_type           VARCHAR(32) NOT NULL, -- CRITICAL_SUMMARY, IPS_BUNDLE
    content_encrypted   BYTEA NOT NULL, -- AES-256-GCM encrypted CBOR
    content_hash        VARCHAR(64) NOT NULL, -- SHA-256 of plaintext for integrity verification
    compression         VARCHAR(16) DEFAULT 'ZSTD',
    encryption_key_ref  VARCHAR(128) NOT NULL, -- reference to key in tshepo-keys-service
    size_bytes          INT NOT NULL,
    version             INT NOT NULL DEFAULT 1,
    source_encounter_id VARCHAR(128),
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    synced_to_card_at   TIMESTAMPTZ,
    CONSTRAINT uq_mushe_card_health UNIQUE (card_id, data_type)
);

-- ── Card Update Queue (pending writes to cards) ─────────────────────────

CREATE TABLE mushe.card_update_queue (
    id                  BIGSERIAL PRIMARY KEY,
    queue_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    card_id             UUID NOT NULL REFERENCES mushe.cards(card_id),
    update_type         VARCHAR(32) NOT NULL, -- HEALTH_SUMMARY, IPS_BUNDLE, PIN_CHANGE, BLOCK, ACTIVATE
    payload_encrypted   BYTEA,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING, WRITING, COMPLETED, FAILED
    priority            INT DEFAULT 5,
    attempts            INT DEFAULT 0,
    last_attempt_at     TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_mushe_card_queue_pending ON mushe.card_update_queue (card_id, status) WHERE status = 'PENDING';

-- ── Event Outbox ────────────────────────────────────────────────────────

CREATE TABLE mushe.event_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        VARCHAR(255) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      INT NOT NULL DEFAULT 1,
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255) NOT NULL,
    producer            VARCHAR(64) NOT NULL DEFAULT 'mushe-wallet-service',
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255) NOT NULL,
    subject_type        VARCHAR(64) NOT NULL,
    partition_key       VARCHAR(255),
    occurred_at         TIMESTAMPTZ NOT NULL,
    payload_json        JSONB NOT NULL,
    publish_error       TEXT,
    retry_count         INT DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_mushe_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_mushe_outbox_unpublished ON mushe.event_outbox (created_at) WHERE published_at IS NULL;
