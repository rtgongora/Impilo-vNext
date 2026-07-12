-- V004: Msika completion wave — real pricing, Rx token seam, Nhume dispatch linkage,
-- COSTA charge linkage, refund execution index, vendor actor binding.

-- Cart items carry the server-resolved price snapshot (listing = pricing SoT).
ALTER TABLE mf_cart_items ADD COLUMN IF NOT EXISTS listing_id  VARCHAR(64);
ALTER TABLE mf_cart_items ADD COLUMN IF NOT EXISTS unit_price  NUMERIC(12,2);
ALTER TABLE mf_cart_items ADD COLUMN IF NOT EXISTS currency    VARCHAR(3);

-- Rx orders persist the verified share-slip token reference.
ALTER TABLE mf_orders ADD COLUMN IF NOT EXISTS rx_token_ref          VARCHAR(255);
ALTER TABLE mf_orders ADD COLUMN IF NOT EXISTS rx_token_subject_type VARCHAR(64);

-- Nhume is dispatch SoR; the plan keeps the delivery linkage.
ALTER TABLE mf_delivery_plans ADD COLUMN IF NOT EXISTS nhume_delivery_id VARCHAR(64);

-- COSTA ChargeRecord linkage (money truth) on the settlement.
ALTER TABLE mf_settlements ADD COLUMN IF NOT EXISTS costa_charge_ref VARCHAR(64);

-- JWT-principal binding for the BFF vendor-me seam (nullable; ops-gated bind).
ALTER TABLE mf_vendor_profiles ADD COLUMN IF NOT EXISTS actor_binding VARCHAR(128);

-- Refund completion is resolved by the real MusheX refund id.
CREATE INDEX IF NOT EXISTS idx_mf_refunds_mushex_refund ON mf_refunds (mushex_refund_id);
CREATE INDEX IF NOT EXISTS idx_mf_vendor_profiles_actor_binding ON mf_vendor_profiles (actor_binding);
