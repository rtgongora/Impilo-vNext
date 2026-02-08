-- ============================================================================
-- MUSHEX — Health Payments, Remittances & Claims Switching Service
-- V001: Full schema initialization
-- ============================================================================

-- ── Payment Intents ──────────────────────────────────────────────────────────
CREATE TABLE mushex_payment_intents (
    intent_id       VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID,
    source_type     VARCHAR(30)     NOT NULL,
    source_id       VARCHAR(128)    NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    amount_total    NUMERIC(14,2)   NOT NULL,
    amount_paid     NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    status          VARCHAR(30)     NOT NULL DEFAULT 'CREATED',
    idempotency_key VARCHAR(128)    NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    lock_version    INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_mushex_intents_tenant_status
    ON mushex_payment_intents (tenant_id, status, created_at DESC);
CREATE INDEX idx_mushex_intents_source
    ON mushex_payment_intents (source_type, source_id);

-- ── Payment Attempts ─────────────────────────────────────────────────────────
CREATE TABLE mushex_payment_attempts (
    id              VARCHAR(26)     PRIMARY KEY,
    intent_id       VARCHAR(26)     NOT NULL REFERENCES mushex_payment_intents(intent_id),
    adapter_type    VARCHAR(30)     NOT NULL,
    adapter_ref     VARCHAR(200),
    amount          NUMERIC(14,2)   NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    requested_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    raw_summary     JSONB
);

CREATE UNIQUE INDEX idx_mushex_attempts_adapter_ref
    ON mushex_payment_attempts (adapter_ref) WHERE adapter_ref IS NOT NULL;
CREATE INDEX idx_mushex_attempts_intent
    ON mushex_payment_attempts (intent_id);

-- ── Ledger Accounts ──────────────────────────────────────────────────────────
CREATE TABLE mushex_ledger_accounts (
    account_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    account_type    VARCHAR(20)     NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_mushex_accounts_tenant_name
    ON mushex_ledger_accounts (tenant_id, name);

-- ── Ledger Entries (double-entry-lite journal) ───────────────────────────────
CREATE TABLE mushex_ledger_entries (
    entry_id        VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    intent_id       VARCHAR(26),
    reference_type  VARCHAR(50)     NOT NULL,
    reference_id    VARCHAR(128)    NOT NULL,
    debit_account   VARCHAR(26)     NOT NULL REFERENCES mushex_ledger_accounts(account_id),
    credit_account  VARCHAR(26)     NOT NULL REFERENCES mushex_ledger_accounts(account_id),
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_ledger_tenant_date
    ON mushex_ledger_entries (tenant_id, created_at DESC);
CREATE INDEX idx_mushex_ledger_intent
    ON mushex_ledger_entries (intent_id) WHERE intent_id IS NOT NULL;

-- ── Remittance Tokens ────────────────────────────────────────────────────────
CREATE TABLE mushex_remittance_tokens (
    id              VARCHAR(26)     PRIMARY KEY,
    intent_id       VARCHAR(26)     NOT NULL REFERENCES mushex_payment_intents(intent_id),
    token_hash      VARCHAR(128)    NOT NULL,
    otp_hash        VARCHAR(128)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    claimed_at      TIMESTAMPTZ,
    claim_meta      JSONB
);

CREATE INDEX idx_mushex_remittance_token_hash
    ON mushex_remittance_tokens (token_hash);
CREATE INDEX idx_mushex_remittance_intent
    ON mushex_remittance_tokens (intent_id);

-- ── Receipts ─────────────────────────────────────────────────────────────────
CREATE TABLE mushex_receipts (
    receipt_id      VARCHAR(26)     PRIMARY KEY,
    intent_id       VARCHAR(26)     NOT NULL REFERENCES mushex_payment_intents(intent_id),
    landela_doc_id  VARCHAR(128),
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    summary         JSONB           NOT NULL
);

CREATE INDEX idx_mushex_receipts_intent
    ON mushex_receipts (intent_id);

-- ── Refunds ──────────────────────────────────────────────────────────────────
CREATE TABLE mushex_refunds (
    refund_id       VARCHAR(26)     PRIMARY KEY,
    intent_id       VARCHAR(26)     NOT NULL REFERENCES mushex_payment_intents(intent_id),
    amount          NUMERIC(14,2)   NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    adapter_ref     VARCHAR(200),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_mushex_refunds_intent
    ON mushex_refunds (intent_id);

-- ── Insurer Profiles ─────────────────────────────────────────────────────────
CREATE TABLE mushex_insurer_profiles (
    insurer_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    mode            VARCHAR(20)     NOT NULL DEFAULT 'REST',
    config_encrypted JSONB,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_insurers_tenant
    ON mushex_insurer_profiles (tenant_id, enabled);

-- ── Claims ───────────────────────────────────────────────────────────────────
CREATE TABLE mushex_claims (
    claim_id        VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    bill_id         VARCHAR(128)    NOT NULL,
    insurer_id      VARCHAR(26)     NOT NULL REFERENCES mushex_insurer_profiles(insurer_id),
    status          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    totals          JSONB,
    submitted_at    TIMESTAMPTZ,
    adjudicated_at  TIMESTAMPTZ,
    external_ref    VARCHAR(200),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_claims_tenant_insurer
    ON mushex_claims (tenant_id, insurer_id, status, submitted_at DESC);
CREATE INDEX idx_mushex_claims_bill
    ON mushex_claims (bill_id);

-- ── Claim Events ─────────────────────────────────────────────────────────────
CREATE TABLE mushex_claim_events (
    id              VARCHAR(26)     PRIMARY KEY,
    claim_id        VARCHAR(26)     NOT NULL REFERENCES mushex_claims(claim_id),
    from_state      VARCHAR(30),
    to_state        VARCHAR(30)     NOT NULL,
    actor_id        VARCHAR(128),
    reason          TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_claim_events_claim
    ON mushex_claim_events (claim_id, created_at);

-- ── Claim Attachments ────────────────────────────────────────────────────────
CREATE TABLE mushex_claim_attachments (
    id              VARCHAR(26)     PRIMARY KEY,
    claim_id        VARCHAR(26)     NOT NULL REFERENCES mushex_claims(claim_id),
    landela_doc_id  VARCHAR(128)    NOT NULL,
    doc_type        VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_claim_attachments_claim
    ON mushex_claim_attachments (claim_id);

-- ── Adjudications ────────────────────────────────────────────────────────────
CREATE TABLE mushex_adjudications (
    id              VARCHAR(26)     PRIMARY KEY,
    claim_id        VARCHAR(26)     NOT NULL REFERENCES mushex_claims(claim_id),
    decision        JSONB           NOT NULL,
    patient_residual NUMERIC(14,2)  NOT NULL DEFAULT 0.00,
    insurer_payable NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_adjudications_claim
    ON mushex_adjudications (claim_id);

-- ── Settlements ──────────────────────────────────────────────────────────────
CREATE TABLE mushex_settlements (
    settlement_id   VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    period_start    DATE            NOT NULL,
    period_end      DATE            NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    totals          JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_settlements_tenant
    ON mushex_settlements (tenant_id, status, period_end DESC);

-- ── Payout Batches ───────────────────────────────────────────────────────────
CREATE TABLE mushex_payout_batches (
    batch_id        VARCHAR(26)     PRIMARY KEY,
    settlement_id   VARCHAR(26)     NOT NULL REFERENCES mushex_settlements(settlement_id),
    adapter_type    VARCHAR(30)     NOT NULL,
    destination_ref JSONB,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    released_at     TIMESTAMPTZ
);

CREATE INDEX idx_mushex_batches_settlement
    ON mushex_payout_batches (settlement_id);

-- ── Payout Items ─────────────────────────────────────────────────────────────
CREATE TABLE mushex_payout_items (
    id              VARCHAR(26)     PRIMARY KEY,
    batch_id        VARCHAR(26)     NOT NULL REFERENCES mushex_payout_batches(batch_id),
    payee_type      VARCHAR(30)     NOT NULL,
    payee_ref       VARCHAR(200)    NOT NULL,
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_payout_items_batch
    ON mushex_payout_items (batch_id);

-- ── Fraud Flags ──────────────────────────────────────────────────────────────
CREATE TABLE mushex_fraud_flags (
    id              VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    kind            VARCHAR(50)     NOT NULL,
    severity        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(128)    NOT NULL,
    evidence        JSONB,
    status          VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_fraud_tenant
    ON mushex_fraud_flags (tenant_id, status, created_at DESC);

-- ── Ops Reviews ──────────────────────────────────────────────────────────────
CREATE TABLE mushex_ops_reviews (
    id              VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    queue_type      VARCHAR(50)     NOT NULL,
    entity_type     VARCHAR(50)     NOT NULL,
    entity_id       VARCHAR(128)    NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    assigned_to     VARCHAR(128),
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_mushex_reviews_tenant
    ON mushex_ops_reviews (tenant_id, status, queue_type);

-- ── Reconciliation Queue ─────────────────────────────────────────────────────
CREATE TABLE mushex_recon_queue (
    id              VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    statement_ref   VARCHAR(200)    NOT NULL,
    statement_date  DATE            NOT NULL,
    amount          NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    counterparty    VARCHAR(200),
    status          VARCHAR(30)     NOT NULL DEFAULT 'UNMATCHED',
    matched_intent_id VARCHAR(26)   REFERENCES mushex_payment_intents(intent_id),
    imported_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    matched_at      TIMESTAMPTZ
);

CREATE INDEX idx_mushex_recon_tenant
    ON mushex_recon_queue (tenant_id, status);

-- ── Event Outbox ─────────────────────────────────────────────────────────────
CREATE TABLE mushex_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mushex_outbox_unpublished
    ON mushex_event_outbox (created_at) WHERE published_at IS NULL;

-- ── Idempotency ──────────────────────────────────────────────────────────────
CREATE TABLE mushex_idempotency (
    idempotency_key VARCHAR(200)    PRIMARY KEY,
    source_system   VARCHAR(50)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);
