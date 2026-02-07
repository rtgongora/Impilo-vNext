-- =============================================================================
-- VITO — Health Payments Wallet (Double-Entry Ledger)
-- Stored-value payments with signed offline transaction support.
-- Every transaction creates exactly two journal entries (debit + credit).
-- =============================================================================

CREATE TABLE vito.wallet (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    health_id       UUID         NOT NULL REFERENCES vito.client(health_id),
    card_id         BIGINT       REFERENCES vito.smart_card(id),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'ZWL', -- ISO 4217
    balance         NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FROZEN, CLOSED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_wallet_per_client UNIQUE (tenant_id, health_id, currency),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_wallet_health_id ON vito.wallet (tenant_id, health_id);

-- Journal: immutable double-entry ledger
-- Every monetary movement is recorded as two entries: one DEBIT, one CREDIT.
CREATE TABLE vito.wallet_journal (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID          NOT NULL,
    transaction_ref     VARCHAR(100)  NOT NULL,          -- idempotency key
    wallet_id           BIGINT        NOT NULL REFERENCES vito.wallet(id),
    entry_type          VARCHAR(10)   NOT NULL,          -- DEBIT, CREDIT
    amount              NUMERIC(15,2) NOT NULL,
    running_balance     NUMERIC(15,2) NOT NULL,          -- balance after this entry
    counterparty_type   VARCHAR(50)   NOT NULL,          -- FACILITY, PROVIDER, SYSTEM, TOPUP
    counterparty_ref    VARCHAR(255),                    -- facility_id, provider_cpid, etc.
    description         VARCHAR(500),
    offline_signature   TEXT,                             -- JWS signature for offline txns
    offline_timestamp   TIMESTAMPTZ,                     -- when the offline txn was signed
    reconciled          BOOLEAN       NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_journal_entry UNIQUE (tenant_id, transaction_ref, entry_type)
);

CREATE INDEX idx_journal_wallet  ON vito.wallet_journal (wallet_id, created_at DESC);
CREATE INDEX idx_journal_txn_ref ON vito.wallet_journal (tenant_id, transaction_ref);
CREATE INDEX idx_journal_unreconciled ON vito.wallet_journal (tenant_id, reconciled)
    WHERE reconciled = false;
