-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V006__dura_batch_lots_reservations.sql
-- Prefix  : inv_
--
-- Dura Wave 3: first-class batch/lot/expiry tracking and stock
-- reservations/allocations.
--
-- Reservations reduce AVAILABLE stock (= on-hand − active reservations)
-- without mutating the on-hand ledger projection. Actual deduction remains
-- a ledger event posted by the consuming workflow.
-- =============================================

-- ─────────────────────────────────────────────
-- 1. inv_batch_lots
--    First-class batch/lot with expiry and a
--    governance status (active / quarantined /
--    recalled / expired / depleted).
-- ─────────────────────────────────────────────
CREATE TABLE inv_batch_lots (
    batch_lot_id     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,
    item_id          UUID          NOT NULL REFERENCES inv_items(item_id),
    item_code        VARCHAR(50)   NOT NULL,
    batch_number     VARCHAR(100)  NOT NULL,
    lot_number       VARCHAR(100),
    expiry_date      DATE,
    manufacture_date DATE,
    manufacturer     VARCHAR(255),
    supplier         VARCHAR(255),
    serial_number    VARCHAR(100),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, item_id, batch_number)
);

CREATE INDEX idx_batch_lots_tenant_item ON inv_batch_lots(tenant_id, item_id);
CREATE INDEX idx_batch_lots_expiry      ON inv_batch_lots(tenant_id, expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_batch_lots_status      ON inv_batch_lots(tenant_id, status);

-- ─────────────────────────────────────────────
-- 2. inv_stock_reservations
--    Soft holds on stock for a workflow (dispense,
--    procedure, transfer, order). Status:
--    ACTIVE / RELEASED / CONSUMED / EXPIRED.
-- ─────────────────────────────────────────────
CREATE TABLE inv_stock_reservations (
    reservation_id UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    facility_id    UUID          NOT NULL,
    store_id       UUID          NOT NULL,
    item_code      VARCHAR(50)   NOT NULL,
    batch_lot_id   UUID          REFERENCES inv_batch_lots(batch_lot_id),
    qty            INTEGER       NOT NULL,
    uom            VARCHAR(20),
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    ref_type       VARCHAR(40),
    ref_id         VARCHAR(100),
    reserved_by    VARCHAR(100),
    reason         VARCHAR(255),
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_reservations_active   ON inv_stock_reservations(tenant_id, facility_id, store_id, item_code, status);
CREATE INDEX idx_reservations_ref      ON inv_stock_reservations(ref_type, ref_id) WHERE ref_type IS NOT NULL;
CREATE INDEX idx_reservations_batch    ON inv_stock_reservations(batch_lot_id) WHERE batch_lot_id IS NOT NULL;
