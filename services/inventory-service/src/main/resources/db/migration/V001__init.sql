-- =============================================
-- Service : inventory-service
-- Database: inventory
-- Flyway  : V001__init.sql
-- Prefix  : inv_
--
-- 14 tables + indexes for the Inventory &
-- Supply Chain Service foundation.
-- =============================================

-- ─────────────────────────────────────────────
-- 1. inv_facilities
--    Registered facilities that hold inventory.
-- ─────────────────────────────────────────────
CREATE TABLE inv_facilities (
    facility_id   UUID          PRIMARY KEY,
    tenant_id     UUID          NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    facility_code VARCHAR(50),
    config        JSONB,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 2. inv_stores
--    Logical or physical storage areas within a
--    facility (main store, pharmacy, ward, etc.)
-- ─────────────────────────────────────────────
CREATE TABLE inv_stores (
    store_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id     UUID          NOT NULL REFERENCES inv_facilities(facility_id),
    tenant_id       UUID          NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    store_type      VARCHAR(30)   NOT NULL DEFAULT 'GENERAL',
    parent_store_id UUID          REFERENCES inv_stores(store_id),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 3. inv_bins
--    Sub-locations within a store (shelf, cold
--    chain, controlled, quarantine, etc.)
-- ─────────────────────────────────────────────
CREATE TABLE inv_bins (
    bin_id     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id   UUID          NOT NULL REFERENCES inv_stores(store_id),
    tenant_id  UUID          NOT NULL,
    name       VARCHAR(100)  NOT NULL,
    bin_type   VARCHAR(30)   NOT NULL DEFAULT 'GENERAL',
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 4. inv_items
--    Master catalogue of inventory items
--    (drugs, consumables, supplies).
-- ─────────────────────────────────────────────
CREATE TABLE inv_items (
    item_id    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID          NOT NULL,
    item_code  VARCHAR(50)   NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    uom        VARCHAR(20)   NOT NULL,
    barcode    VARCHAR(100),
    category   VARCHAR(100),
    controlled BOOLEAN       NOT NULL DEFAULT FALSE,
    zibo_refs  JSONB,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, item_code)
);

-- ─────────────────────────────────────────────
-- 5. inv_ledger_events  (append-only)
--    Every stock movement is recorded as an
--    immutable ledger event with a signed qty
--    delta. This is the source of truth.
-- ─────────────────────────────────────────────
CREATE TABLE inv_ledger_events (
    event_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    facility_id     UUID          NOT NULL,
    store_id        UUID          NOT NULL,
    bin_id          UUID,
    item_code       VARCHAR(50)   NOT NULL,
    batch           VARCHAR(50),
    expiry          DATE,
    qty_delta       INTEGER       NOT NULL,
    uom             VARCHAR(20),
    event_type      VARCHAR(20)   NOT NULL,
    reason          VARCHAR(100),
    ref_type        VARCHAR(50),
    ref_id          VARCHAR(128),
    idempotency_key VARCHAR(128),
    actor_id        VARCHAR(128)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 6. inv_on_hand  (projection / materialised view)
--    Current stock-on-hand per item/batch/bin.
--    Updated whenever a ledger event is recorded.
-- ─────────────────────────────────────────────
CREATE TABLE inv_on_hand (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    facility_id UUID          NOT NULL,
    store_id    UUID          NOT NULL,
    bin_id      UUID,
    item_code   VARCHAR(50)   NOT NULL,
    batch       VARCHAR(50),
    expiry      DATE,
    qty_on_hand INTEGER       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 7. inv_count_sessions
--    Physical stock count sessions.
-- ─────────────────────────────────────────────
CREATE TABLE inv_count_sessions (
    session_id   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL,
    facility_id  UUID          NOT NULL,
    store_id     UUID          NOT NULL,
    bin_id       UUID,
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_by   VARCHAR(128)  NOT NULL,
    submitted_by VARCHAR(128),
    submitted_at TIMESTAMPTZ,
    approved_by  VARCHAR(128),
    approved_at  TIMESTAMPTZ,
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 8. inv_count_lines
--    Individual line items within a count session.
-- ─────────────────────────────────────────────
CREATE TABLE inv_count_lines (
    line_id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID          NOT NULL REFERENCES inv_count_sessions(session_id),
    item_code       VARCHAR(50)   NOT NULL,
    batch           VARCHAR(50),
    expiry          DATE,
    qty_system      INTEGER       NOT NULL DEFAULT 0,
    qty_counted     INTEGER,
    variance        INTEGER,
    barcode_scanned VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 9. inv_requisitions
--    Internal stock requisitions (inter-store or
--    central warehouse requests).
-- ─────────────────────────────────────────────
CREATE TABLE inv_requisitions (
    req_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID          NOT NULL,
    facility_id   UUID          NOT NULL,
    from_store_id UUID          NOT NULL,
    to_store_id   UUID          NOT NULL,
    status        VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    priority      VARCHAR(10)   NOT NULL DEFAULT 'ROUTINE',
    requested_by  VARCHAR(128)  NOT NULL,
    approved_by   VARCHAR(128),
    approved_at   TIMESTAMPTZ,
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 10. inv_requisition_lines
--     Individual line items within a requisition.
-- ─────────────────────────────────────────────
CREATE TABLE inv_requisition_lines (
    line_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    req_id        UUID          NOT NULL REFERENCES inv_requisitions(req_id),
    item_code     VARCHAR(50)   NOT NULL,
    qty_requested INTEGER       NOT NULL,
    qty_approved  INTEGER,
    qty_fulfilled INTEGER       NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 11. inv_handovers
--     Shift handover records for stock custody
--     transfers between actors.
-- ─────────────────────────────────────────────
CREATE TABLE inv_handovers (
    handover_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL,
    facility_id        UUID          NOT NULL,
    store_id           UUID          NOT NULL,
    from_actor         VARCHAR(128)  NOT NULL,
    to_actor           VARCHAR(128)  NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'INITIATED',
    outgoing_signed_at TIMESTAMPTZ,
    incoming_signed_at TIMESTAMPTZ,
    notes              TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 12. inv_reconcile_queue
--     Discrepancies between internal and external
--     (eLMIS) stock data awaiting operator review.
-- ─────────────────────────────────────────────
CREATE TABLE inv_reconcile_queue (
    rec_id       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID           NOT NULL,
    facility_id  UUID           NOT NULL,
    store_id     UUID           NOT NULL,
    item_code    VARCHAR(50)    NOT NULL,
    batch        VARCHAR(50),
    internal_qty INTEGER,
    external_qty INTEGER,
    confidence   NUMERIC(5,4)   NOT NULL DEFAULT 0.0000,
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payload      JSONB,
    ops_notes    TEXT,
    resolved_by  VARCHAR(128),
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 13. inv_capabilities
--     Per-tenant (optionally per-facility)
--     configuration of the inventory operating
--     mode (INTERNAL, ADAPTER, HYBRID).
-- ─────────────────────────────────────────────
CREATE TABLE inv_capabilities (
    cap_id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    facility_id     UUID,
    capability_mode VARCHAR(10)   NOT NULL DEFAULT 'INTERNAL',
    config          JSONB,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 14. inv_event_outbox
--     Transactional outbox for reliable Kafka
--     event publishing.
-- ─────────────────────────────────────────────
CREATE TABLE inv_event_outbox (
    id             BIGSERIAL      PRIMARY KEY,
    aggregate_type VARCHAR(50)    NOT NULL,
    aggregate_id   VARCHAR(128)   NOT NULL,
    event_type     VARCHAR(100)   NOT NULL,
    payload        JSONB          NOT NULL,
    tenant_id      UUID           NOT NULL,
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);


-- ═════════════════════════════════════════════
-- INDEXES
-- ═════════════════════════════════════════════

-- inv_ledger_events
CREATE INDEX idx_ledger_facility_store_item ON inv_ledger_events(facility_id, store_id, item_code);
CREATE INDEX idx_ledger_item_batch_expiry   ON inv_ledger_events(item_code, batch, expiry);
CREATE INDEX idx_ledger_created_desc        ON inv_ledger_events(created_at DESC);
CREATE UNIQUE INDEX idx_ledger_idempotency  ON inv_ledger_events(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- inv_on_hand
CREATE UNIQUE INDEX idx_on_hand_composite ON inv_on_hand (
    tenant_id, facility_id, store_id,
    COALESCE(bin_id, '00000000-0000-0000-0000-000000000000'::UUID),
    item_code,
    COALESCE(batch, '___NONE___'),
    COALESCE(expiry, '9999-12-31'::DATE)
);
CREATE INDEX idx_on_hand_store_item ON inv_on_hand(store_id, item_code);
CREATE INDEX idx_on_hand_expiry     ON inv_on_hand(expiry) WHERE expiry IS NOT NULL;

-- inv_items
CREATE INDEX idx_items_barcode      ON inv_items(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_items_tenant_code  ON inv_items(tenant_id, item_code);

-- inv_stores
CREATE INDEX idx_stores_facility ON inv_stores(facility_id);

-- inv_bins
CREATE INDEX idx_bins_store ON inv_bins(store_id);

-- inv_count_sessions
CREATE INDEX idx_count_sessions_status ON inv_count_sessions(status);

-- inv_requisitions
CREATE INDEX idx_requisitions_status ON inv_requisitions(status);

-- inv_reconcile_queue
CREATE INDEX idx_reconcile_status_conf ON inv_reconcile_queue(status, confidence);

-- inv_event_outbox
CREATE INDEX idx_outbox_unpublished ON inv_event_outbox(created_at) WHERE published_at IS NULL;

-- inv_capabilities
CREATE UNIQUE INDEX idx_capabilities_tenant_facility ON inv_capabilities (
    tenant_id,
    COALESCE(facility_id, '00000000-0000-0000-0000-000000000000'::UUID)
);
CREATE INDEX idx_capabilities_tenant ON inv_capabilities(tenant_id, facility_id);
