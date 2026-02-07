-- =============================================
-- Service : pharmacy-service
-- Database: pharmacy
-- Flyway  : V001__init.sql
-- Prefix  : rx_
-- =============================================

-- ── Dispense Orders ────────────────────────────────────────────────────
-- Each row represents a single dispense order received from OROS.
-- Status follows the DispenseStatus enum lifecycle.
CREATE TABLE rx_dispense_orders (
    order_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    workspace_id    UUID,
    store_id        VARCHAR(50),
    patient_cpid    VARCHAR(64) NOT NULL,
    oros_order_id   VARCHAR(26) NOT NULL UNIQUE,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(10) NOT NULL DEFAULT 'ROUTINE',
    prescriber_id   VARCHAR(128),
    prescriber_notes TEXT,
    clinical_notes  TEXT,
    accepted_by     VARCHAR(128),
    accepted_at     TIMESTAMPTZ,
    completed_by    VARCHAR(128),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Dispense Items ─────────────────────────────────────────────────────
-- Line-items within a dispense order. Each row tracks a single drug
-- through picking, dispensing, and optional substitution.
CREATE TABLE rx_dispense_items (
    item_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_order_id   UUID NOT NULL REFERENCES rx_dispense_orders(order_id),
    drug_code           VARCHAR(50) NOT NULL,
    drug_display        VARCHAR(255),
    qty_requested       INTEGER NOT NULL,
    qty_dispensed       INTEGER NOT NULL DEFAULT 0,
    qty_remaining       INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    batch_number        VARCHAR(50),
    expiry_date         DATE,
    barcode             VARCHAR(100),
    substitution        JSONB,
    instructions        TEXT,
    specimen_type       VARCHAR(50),
    body_site           VARCHAR(50),
    picked_by           VARCHAR(128),
    picked_at           TIMESTAMPTZ,
    dispensed_by        VARCHAR(128),
    dispensed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Stock Movements ────────────────────────────────────────────────────
-- Immutable ledger of every stock change. qty_delta is positive for
-- receipts/returns/adjustments-up and negative for dispenses/wastage.
CREATE TABLE rx_stock_movements (
    movement_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    store_id        VARCHAR(50) NOT NULL,
    bin_id          VARCHAR(50),
    item_code       VARCHAR(50) NOT NULL,
    item_display    VARCHAR(255),
    batch_number    VARCHAR(50),
    expiry_date     DATE,
    qty_delta       INTEGER NOT NULL,
    movement_type   VARCHAR(20) NOT NULL,
    reason          VARCHAR(50),
    ref_type        VARCHAR(30),
    ref_id          VARCHAR(128),
    recorded_by     VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Formulary ──────────────────────────────────────────────────────────
-- Per-tenant (optionally per-facility) drug allowlist with optional
-- constraints (e.g. max qty, restricted prescriber roles) stored as JSONB.
CREATE TABLE rx_formulary (
    formulary_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID,
    drug_code       VARCHAR(50) NOT NULL,
    drug_display    VARCHAR(255),
    allowed         BOOLEAN NOT NULL DEFAULT TRUE,
    constraints     JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, facility_id, drug_code)
);

-- ── Substitution Rules ─────────────────────────────────────────────────
-- Configurable rules governing drug substitution when the prescribed
-- drug is unavailable. rule_json holds additional constraints.
CREATE TABLE rx_substitution_rules (
    rule_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID,
    rule_type       VARCHAR(30) NOT NULL,
    source_code     VARCHAR(50) NOT NULL,
    target_code     VARCHAR(50) NOT NULL,
    rule_json       JSONB,
    requires_step_up BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Pickup Proofs ──────────────────────────────────────────────────────
-- Proof-of-collection records. token_hash stores the hashed OTP or QR
-- payload; the plaintext is never persisted.
CREATE TABLE rx_pickup_proofs (
    proof_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_order_id   UUID NOT NULL REFERENCES rx_dispense_orders(order_id),
    method              VARCHAR(10) NOT NULL,
    token_hash          VARCHAR(128) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at          TIMESTAMPTZ NOT NULL,
    delegated_to        VARCHAR(128),
    claimed_by          VARCHAR(128),
    claimed_at          TIMESTAMPTZ,
    device_fingerprint  VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Reconcile Queue ────────────────────────────────────────────────────
-- Discrepancies between internal stock and eLMIS external records,
-- queued for operator review and resolution.
CREATE TABLE rx_reconcile_queue (
    rec_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    rec_type        VARCHAR(20) NOT NULL DEFAULT 'STOCK',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    item_code       VARCHAR(50),
    internal_qty    INTEGER,
    external_qty    INTEGER,
    confidence      NUMERIC(5,4) NOT NULL DEFAULT 0.0000,
    payload         JSONB,
    ops_notes       TEXT,
    resolved_by     VARCHAR(128),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Event Outbox ───────────────────────────────────────────────────────
-- Transactional outbox for reliable Kafka event publishing.
-- The outbox publisher polls for rows where published_at IS NULL.
CREATE TABLE rx_event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    tenant_id       UUID NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── Indexes ────────────────────────────────────────────────────────────

-- Dispense orders: facility worklist, OROS lookup, patient history, tenant filter
CREATE INDEX idx_rx_dispense_orders_facility_status ON rx_dispense_orders(facility_id, status, created_at DESC);
CREATE INDEX idx_rx_dispense_orders_oros ON rx_dispense_orders(oros_order_id);
CREATE INDEX idx_rx_dispense_orders_patient ON rx_dispense_orders(patient_cpid, created_at DESC);
CREATE INDEX idx_rx_dispense_orders_tenant ON rx_dispense_orders(tenant_id);

-- Dispense items: order lookup, drug lookup
CREATE INDEX idx_rx_dispense_items_order ON rx_dispense_items(dispense_order_id);
CREATE INDEX idx_rx_dispense_items_drug ON rx_dispense_items(drug_code);

-- Stock movements: facility/store inventory, batch FEFO, reference lookup
CREATE INDEX idx_rx_stock_movements_facility ON rx_stock_movements(facility_id, store_id, item_code);
CREATE INDEX idx_rx_stock_movements_batch ON rx_stock_movements(item_code, batch_number, expiry_date);
CREATE INDEX idx_rx_stock_movements_ref ON rx_stock_movements(ref_type, ref_id);

-- Formulary: tenant/facility drug lookup
CREATE INDEX idx_rx_formulary_tenant ON rx_formulary(tenant_id, facility_id, drug_code);

-- Substitution rules: source drug lookup
CREATE INDEX idx_rx_substitution_rules_lookup ON rx_substitution_rules(tenant_id, source_code, enabled);

-- Pickup proofs: order lookup, token verification
CREATE INDEX idx_rx_pickup_proofs_order ON rx_pickup_proofs(dispense_order_id);
CREATE INDEX idx_rx_pickup_proofs_token ON rx_pickup_proofs(token_hash);

-- Reconcile queue: status/confidence priority
CREATE INDEX idx_rx_reconcile_queue_status ON rx_reconcile_queue(status, confidence DESC);

-- Event outbox: pending events for polling
CREATE INDEX idx_rx_event_outbox_pending ON rx_event_outbox(published_at) WHERE published_at IS NULL;
