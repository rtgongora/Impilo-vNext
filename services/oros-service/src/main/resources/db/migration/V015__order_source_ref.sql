-- ============================================================================
-- V015: Order provenance link (TM-B7).
--
-- Orders can now originate from a governed telemedicine consultation
-- (RequestSource.TELECONSULT). source_ref carries the originating object id
-- (the teleconsult referral id) so the fulfilment + result-return trail links
-- back to the virtual-care case, and the duplicate-order guard can query
-- "active order for this referral + this coded item".
-- ============================================================================

ALTER TABLE oros_orders ADD COLUMN IF NOT EXISTS source_ref VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_oros_orders_source_ref
    ON oros_orders (tenant_id, source_ref) WHERE source_ref IS NOT NULL;
