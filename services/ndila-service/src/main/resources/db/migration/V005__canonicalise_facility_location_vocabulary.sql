-- =====================================================================================
-- ndila :: V005 — one vocabulary for facility locations, and a place to hold a geocode
--                 PROPOSAL that is not yet a pin (HAR W4)
--
-- PART 1 — the naming split.
--
-- Three writers have put facility rows into ndila_locations, and one of them disagreed
-- with the other two:
--
--   NdilaFacilityMasterSyncService  owner_service='TUSO'          location_type='HEALTH_FACILITY'
--   HpaLocationImportService        owner_service='TUSO'          location_type='HEALTH_FACILITY'
--   V003 seed migration             owner_service='tuso-service'  location_type='FACILITY'
--
-- The canonical spelling is the runtime one — it is what V001 documents in its column
-- comments, what NdilaFacilityMasterSyncService declares as constants, and what
-- NdilaSpatialSearchService normalises *towards*: its alias map sends FACILITY, CLINIC and
-- HOSPITAL all to HEALTH_FACILITY before querying.
--
-- That last point is the live defect. Because the alias map rewrites a caller's "FACILITY"
-- into "HEALTH_FACILITY", the rows the V003 seed wrote as location_type='FACILITY' can never
-- match a proximity query. Every /nearby search and every Nompilo searchNearbyServices call
-- for a health facility silently skips them — and they are the rows that actually have
-- coordinates. The half of the national map that can be mapped is the half the map cannot
-- see.
--
-- This normalises the seeded rows onto the canonical vocabulary. It does not touch the
-- HPA-imported rows: they were already spelt correctly.
--
-- PART 2 — geocode proposals.
--
-- 5,414 of HPA's 5,423 location rows carry a locality, a province or a street address, and
-- exactly ONE carries a plausible Zimbabwe coordinate. Those 5,414 are geocodable, but a
-- geocoded point is a guess, and a guess rendered as a pin is indistinguishable from a
-- surveyed one. So a proposal lands HERE, on the review queue, and never on ndila_locations.
-- A steward or the claiming facility confirms it; only then does a pin publish.
-- =====================================================================================

-- ---- Part 1: canonicalise the seeded facility rows -----------------------------------

-- Guard first: if a runtime-synced row already owns this entity id under the canonical
-- spelling, normalising the seeded row would leave two rows for one facility. Retire the
-- seeded duplicate instead of promoting it (deactivate, never delete — the row is evidence
-- of what the seed asserted).
UPDATE ndila_locations seeded
   SET is_active = FALSE,
       description = COALESCE(seeded.description || ' | ', '')
                     || 'Superseded by the canonical facility location row (HAR W4).',
       updated_at = NOW()
 WHERE seeded.owner_service = 'tuso-service'
   AND seeded.owner_entity_type = 'FACILITY'
   AND EXISTS (
       SELECT 1 FROM ndila_locations canonical
        WHERE canonical.tenant_id = seeded.tenant_id
          AND canonical.owner_service = 'TUSO'
          AND canonical.owner_entity_type = 'FACILITY'
          AND canonical.owner_entity_id = seeded.owner_entity_id
   );

UPDATE ndila_locations
   SET owner_service = 'TUSO',
       location_type = 'HEALTH_FACILITY',
       updated_at = NOW()
 WHERE owner_service = 'tuso-service'
   AND owner_entity_type = 'FACILITY'
   AND is_active = TRUE;

-- Any remaining facility row still spelt 'FACILITY' came from the same seed lineage.
UPDATE ndila_locations
   SET location_type = 'HEALTH_FACILITY',
       updated_at = NOW()
 WHERE owner_service = 'TUSO'
   AND owner_entity_type = 'FACILITY'
   AND location_type = 'FACILITY';

-- The review queue was seeded from the same lineage and carries the same split.
UPDATE ndila_geocode_review_queue
   SET owner_service = 'TUSO'
 WHERE owner_service = 'tuso-service';

-- ---- Part 2: geocode proposals on the review queue -----------------------------------

ALTER TABLE ndila_geocode_review_queue
    ADD COLUMN IF NOT EXISTS proposed_latitude   NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS proposed_longitude  NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS proposed_source     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS proposed_confidence VARCHAR(32),
    ADD COLUMN IF NOT EXISTS proposed_locality   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS proposed_address    TEXT,
    ADD COLUMN IF NOT EXISTS proposed_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proposal_status     VARCHAR(32);

-- A proposal is not a location. Anything that ever copies these onto ndila_locations has to
-- pass through a confirmation, and this constraint makes the intermediate states explicit.
ALTER TABLE ndila_geocode_review_queue
    DROP CONSTRAINT IF EXISTS chk_ndila_geocode_proposal_status;
ALTER TABLE ndila_geocode_review_queue
    ADD CONSTRAINT chk_ndila_geocode_proposal_status
    CHECK (proposal_status IS NULL
           OR proposal_status IN ('PROPOSED', 'CONFIRMED', 'REJECTED', 'SUPERSEDED'));

-- A proposal must carry both halves of a coordinate or neither.
ALTER TABLE ndila_geocode_review_queue
    DROP CONSTRAINT IF EXISTS chk_ndila_geocode_proposal_pair;
ALTER TABLE ndila_geocode_review_queue
    ADD CONSTRAINT chk_ndila_geocode_proposal_pair
    CHECK ((proposed_latitude IS NULL) = (proposed_longitude IS NULL));

-- ---- Indexes -------------------------------------------------------------------------

-- The steward worklist: pending proposals by province.
CREATE INDEX IF NOT EXISTS idx_ndila_geocode_review_proposal
    ON ndila_geocode_review_queue (tenant_id, proposal_status, province);

-- Proximity search reads by (tenant, location_type); the split made this index useless for
-- half the estate.
CREATE INDEX IF NOT EXISTS idx_ndila_locations_tenant_type_active
    ON ndila_locations (tenant_id, location_type)
    WHERE is_active = TRUE;

-- The by-owner lookup, now that both halves share one owner_service spelling.
CREATE INDEX IF NOT EXISTS idx_ndila_locations_owner
    ON ndila_locations (tenant_id, owner_service, owner_entity_type, owner_entity_id);
