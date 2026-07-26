-- =====================================================================================
-- ndila :: V007 — recover 2,281 HPA street addresses from the column they were filed under
--
-- THE ACTUAL DEFECT, which is not the one anybody (including me) wrote down.
--
-- Both the discovery lane and I concluded the HPA importer was DROPPING street addresses,
-- because live showed `address_line1 = 0` across all 6,322 HPA rows. That conclusion was wrong,
-- and the PO-approved plan built on it ("ingest the 2,227 addresses the importer discards").
--
-- The addresses were never discarded. The original INSERT bound its parameters positionally:
--
--     INSERT INTO ndila_locations (..., name, description, location_type, latitude, ...)
--     VALUES                      (..., ?,    ?,           'HEALTH_FACILITY', ?, ...)
--                                        name  address ← the address landed in `description`
--
-- Measured on the live estate: 2,281 HPA rows carry a `description`, and every sample is a street
-- address — "7 Baines Avenue, Suite 8, Westend Clinic Extension, Harare", "186 Fife Avenue". The
-- count matches the feed exactly: of 6,327 feed rows, 2,294 carry a legacy_enrichment block and
-- 2,281 of those carry a physical_address. Nothing was lost. It was misfiled.
--
-- So this is a column move, not a re-import — smaller, safer, and it makes the addresses available
-- for address-precision geocoding immediately rather than after a re-ingest.
--
-- V004 (the W4 importer change) already writes `address_line1` correctly and leaves `description`
-- null, so future imports do not need this and re-running it is a no-op.
--
-- CONSERVATIVE BY CONSTRUCTION: only HPA-owned, IMPORT-sourced rows, only where `address_line1` is
-- still empty, and only where a `description` exists. A row that has since been given a real
-- address by a steward or a claim is left alone.
-- =====================================================================================

UPDATE ndila_locations
   SET address_line1 = description,
       description   = NULL,
       updated_at    = NOW()
 WHERE owner_entity_id LIKE 'HPA-%'
   AND source = 'IMPORT'
   AND nullif(trim(coalesce(address_line1, '')), '') IS NULL
   AND nullif(trim(coalesce(description,   '')), '') IS NOT NULL;

-- Address-precision geocoding reads this; the suburb fallback does not.
CREATE INDEX IF NOT EXISTS idx_ndila_locations_hpa_address
    ON ndila_locations (tenant_id)
    WHERE owner_entity_id LIKE 'HPA-%' AND address_line1 IS NOT NULL;
