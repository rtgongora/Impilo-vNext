-- =============================================================================
-- V37: Ward Charts — inpatient charting system
--
-- Fluid balance, drug administration, neurological observations (fit chart),
-- turning/pressure area, diet/nutrition, and general observation (NEWS) charts.
--
-- Generic schema: chart_type + entries with JSONB values allows any chart type
-- defined in wardChartTypes.ts to be stored without schema changes.
-- =============================================================================

CREATE TABLE IF NOT EXISTS ward_charts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    patient_id      UUID            NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    encounter_id    UUID,
    chart_type      VARCHAR(32)     NOT NULL,
    ward_id         VARCHAR(255),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    opened_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    opened_by       VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ward_charts_patient ON ward_charts (tenant_id, patient_id, chart_type);
CREATE INDEX idx_ward_charts_encounter ON ward_charts (encounter_id) WHERE encounter_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ward_chart_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chart_id        UUID            NOT NULL REFERENCES ward_charts(id) ON DELETE CASCADE,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    recorded_by     VARCHAR(255)    NOT NULL,
    values_json     JSONB           NOT NULL,
    notes           TEXT,
    alert_triggered BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chart_entries_chart ON ward_chart_entries (chart_id, recorded_at DESC);

-- =============================================================================
-- V37b: Order sets and encounter cart
-- =============================================================================

CREATE TABLE IF NOT EXISTS encounter_order_carts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    encounter_id    UUID            NOT NULL,
    patient_id      UUID            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(255)    NOT NULL,
    submitted_at    TIMESTAMPTZ,
    items_json      JSONB           NOT NULL DEFAULT '[]'::jsonb,
    order_set_id    VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_cart_encounter ON encounter_order_carts (encounter_id);
CREATE INDEX idx_order_cart_patient ON encounter_order_carts (tenant_id, patient_id);

-- =============================================================================
-- V37c: Enhanced stock management
-- =============================================================================

CREATE TABLE IF NOT EXISTS stock_transfers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    from_location   VARCHAR(255)    NOT NULL,
    to_location     VARCHAR(255)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(255)    NOT NULL,
    approved_by     VARCHAR(255),
    items_json      JSONB           NOT NULL DEFAULT '[]'::jsonb,
    reason          TEXT,
    requested_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_stock_xfer_tenant ON stock_transfers (tenant_id, status);

CREATE TABLE IF NOT EXISTS stock_recalls (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255)    NOT NULL,
    product_code    VARCHAR(255)    NOT NULL,
    batch_number    VARCHAR(255),
    reason          TEXT            NOT NULL,
    severity        VARCHAR(32)     NOT NULL DEFAULT 'STANDARD',
    issued_by       VARCHAR(255)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    affected_locations JSONB        NOT NULL DEFAULT '[]'::jsonb,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_recall_active ON stock_recalls (tenant_id, status) WHERE status = 'ACTIVE';
