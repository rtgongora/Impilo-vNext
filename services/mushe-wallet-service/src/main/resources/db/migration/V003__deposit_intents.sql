-- Collection-intent cash-in truth.
--
-- depositFromSource used to credit the ledger immediately with NO external
-- money movement — "deposit from EcoCash" increased the balance without
-- EcoCash ever being charged. External-source deposits now create a PENDING
-- deposit intent that credits the wallet ONLY on confirmation (a provider
-- collection callback, or a matched line on the bank collection account's
-- statement). Cash deposits at a cashier desk remain immediate (the money is
-- physically in hand) but are recorded here too for the reconciliation trail.

CREATE TABLE mushe.deposit_intents (
    id                  BIGSERIAL PRIMARY KEY,
    deposit_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    wallet_id           UUID NOT NULL REFERENCES mushe.wallets(wallet_id),
    source_id           UUID,                     -- funding source used, when any
    channel             VARCHAR(32) NOT NULL,     -- MOBILE_MONEY | BANK_ZIPIT | CASH | CARD | REMITTANCE
    amount              DECIMAL(14,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    -- Short human-typable code the depositor quotes (ZIPIT narrative / provider
    -- reference); statement matching keys on this.
    reference_code      VARCHAR(24) NOT NULL UNIQUE,
    external_ref        VARCHAR(255),             -- provider txn id / statement line ref
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | CONFIRMED | CANCELLED | EXPIRED
    credited_txn_id     UUID,                     -- wallet transaction created on confirm
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        VARCHAR(128)
);

CREATE INDEX idx_deposit_intents_wallet ON mushe.deposit_intents (wallet_id, created_at DESC);
CREATE INDEX idx_deposit_intents_pending ON mushe.deposit_intents (status) WHERE status = 'PENDING';
