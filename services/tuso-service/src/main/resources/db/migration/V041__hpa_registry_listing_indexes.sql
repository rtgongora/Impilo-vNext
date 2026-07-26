-- =====================================================================================
-- tuso :: V041 — Indexes for the find-care registry-listing lane and the bounded
--                capability derivation (HAR W5)
--
-- Index only. No capability row is written by SQL: HpaCapabilityDerivationService writes them,
-- so the narrow rule about WHICH classes may derive a service lives in one place, is unit-tested,
-- and is refused at runtime for OPD/IPD/MATERNITY/EMERGENCY. A migration that inserted
-- capabilities directly would bypass all three.
-- =====================================================================================

-- The second find-care lane: HPA-registered facilities in a caller's province/district that carry
-- no confirmed capability. Partial, because the lane only ever asks about this one status.
CREATE INDEX IF NOT EXISTS idx_facility_registry_listing_area
    ON tuso.facility (tenant_id, province, district)
    WHERE regulatory_status = 'IMPORTED_PENDING_CONFIGURATION';

-- The derivation pass joins the two current regulatory assertions per facility.
CREATE INDEX IF NOT EXISTS idx_ffa_regulatory_class
    ON tuso.facility_field_assertion (facility_id, field_path)
    WHERE is_current AND field_path IN ('regulatory.type', 'regulatory.subtype');

-- Idempotency check on each derivation: does this facility already carry the capability?
CREATE INDEX IF NOT EXISTS idx_facility_capability_facility_code
    ON tuso.facility_capability (facility_id, capability_code);
