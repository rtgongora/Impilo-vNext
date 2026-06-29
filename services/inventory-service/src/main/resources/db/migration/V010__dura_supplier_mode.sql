-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V010__dura_supplier_mode.sql
-- Prefix  : inv_
--
-- Dura Wave: supplier/seller mode. Supplier profiles, published catalogue with
-- pricing/availability, and customer orders (header + lines) with a fulfilment
-- lifecycle. Costing/payment/dispatch (Costa/MusheX/Nhume) integrate later.
-- =============================================

CREATE TABLE inv_supplier_profiles (
    supplier_id    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    type           VARCHAR(20)   NOT NULL DEFAULT 'DISTRIBUTOR',
    licence_number VARCHAR(100),
    contact        VARCHAR(255),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_supplier_profiles_tenant ON inv_supplier_profiles(tenant_id);

CREATE TABLE inv_supplier_catalogue_items (
    catalogue_item_id UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL,
    supplier_id       UUID          NOT NULL REFERENCES inv_supplier_profiles(supplier_id),
    item_code         VARCHAR(50),
    item_name         VARCHAR(255)  NOT NULL,
    pack_size         VARCHAR(100),
    unit_price        NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)    NOT NULL DEFAULT 'USD',
    available_qty     INTEGER       NOT NULL DEFAULT 0,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, supplier_id, item_name)
);

CREATE INDEX idx_supplier_catalogue_supplier ON inv_supplier_catalogue_items(tenant_id, supplier_id);

CREATE TABLE inv_supplier_orders (
    supplier_order_id UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL,
    supplier_id       UUID          NOT NULL REFERENCES inv_supplier_profiles(supplier_id),
    buyer_facility_id UUID,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PLACED',
    total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)    NOT NULL DEFAULT 'USD',
    placed_by         VARCHAR(100),
    notes             VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    dispatched_at     TIMESTAMPTZ,
    delivered_at      TIMESTAMPTZ
);

CREATE INDEX idx_supplier_orders_supplier ON inv_supplier_orders(tenant_id, supplier_id, status);
CREATE INDEX idx_supplier_orders_buyer    ON inv_supplier_orders(tenant_id, buyer_facility_id);

CREATE TABLE inv_supplier_order_lines (
    line_id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_order_id UUID          NOT NULL REFERENCES inv_supplier_orders(supplier_order_id),
    item_code         VARCHAR(50),
    item_name         VARCHAR(255)  NOT NULL,
    qty               INTEGER       NOT NULL,
    unit_price        NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total        NUMERIC(18,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_supplier_order_lines_order ON inv_supplier_order_lines(supplier_order_id);
