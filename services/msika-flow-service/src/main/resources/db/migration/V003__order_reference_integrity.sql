-- =============================================================================
-- MSIKA-FLOW V003 — Cross-service reference integrity (facility/vendor refs)
-- Adds string reference columns to avoid UUID-vs-Long mismatches across services.
-- These are additive and backward-compatible with existing UUID columns.
-- =============================================================================

ALTER TABLE mf_orders
    ADD COLUMN IF NOT EXISTS facility_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS vendor_ref   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_ref VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_mf_orders_facility_ref ON mf_orders (facility_ref);
CREATE INDEX IF NOT EXISTS idx_mf_orders_vendor_ref ON mf_orders (vendor_ref);
CREATE INDEX IF NOT EXISTS idx_mf_orders_provider_ref ON mf_orders (provider_ref);

