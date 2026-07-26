-- =====================================================================================
-- ndila :: V006 — a curated place gazetteer, and a second precision tier (HAR W7)
--
-- WHY A TABLE AND NOT A COMPUTATION.
--
-- V005 shipped HpaGeocodeProposalService, which proposed a district centroid for each pending
-- review row. Measured against the live estate afterwards, it was inert: of 6,327 queue rows,
-- ZERO carry a district and ZERO carry a proposed locality — those columns are written only by the
-- new importer path, i.e. by imports that have not happened yet. It would have proposed nothing and
-- reported success. This migration replaces that keying.
--
-- The key that actually exists in the data is (locality, province), and there are only **212
-- distinct pairs** across the 2,294 HPA rows that carry a locality. 212 is small enough to place
-- once, have a human check every row, and keep as reviewed data — which is a far better artefact
-- than a number a service recomputes from whatever happens to be in the table that day.
--
-- TWO PRECISION TIERS, DELIBERATELY NOT BLENDED.
--
-- The source dump carries 2,227 street addresses that the importer silently dropped (it reads an
-- address only when a legacy_enrichment block is present). Those can eventually be placed at
-- ADDRESS precision. Everything else falls back to SUBURB_CENTROID, where every facility in
-- CITY CENTRE / Harare shares one point.
--
-- These are different claims about the world — "this is where it is" versus "it is somewhere in
-- here" — so they are stored as different precisions and must be rendered differently. A shared
-- suburb centroid displayed as "1.2 km" would be a precise-looking lie, which is the exact class of
-- defect this whole programme has been removing.
--
-- NOTHING HERE PUBLISHES A PIN. The gazetteer feeds proposals onto ndila_geocode_review_queue;
-- a steward still confirms before anything reaches ndila_locations.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS ndila_place_gazetteer (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,

    -- The key as it appears in the data, plus a normalised form for matching.
    locality        VARCHAR(128) NOT NULL,
    province        VARCHAR(128) NOT NULL,
    locality_key    VARCHAR(128) NOT NULL,   -- upper(trim(locality))
    province_key    VARCHAR(128) NOT NULL,   -- upper(trim(province))

    -- NULL until someone places it. A row with no coordinate is a worklist item, not a failure.
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),

    -- ADDRESS  — derived from a specific street address; describes one building.
    -- SUBURB_CENTROID — one point shared by every facility in the suburb.
    precision_tier  VARCHAR(24) NOT NULL DEFAULT 'SUBURB_CENTROID',

    -- Where the coordinate came from, in words a reviewer can check.
    coordinate_source VARCHAR(128),

    -- How many HPA facilities sit behind this pair — the reviewer's sense of blast radius.
    facility_count  INT NOT NULL DEFAULT 0,

    -- PENDING_COORDINATES → PROPOSED → REVIEWED → REJECTED
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING_COORDINATES',
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ,
    review_note     TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_ndila_gazetteer_place UNIQUE (tenant_id, locality_key, province_key)
);

-- A coordinate is both halves or neither.
ALTER TABLE ndila_place_gazetteer
    DROP CONSTRAINT IF EXISTS chk_ndila_gazetteer_pair;
ALTER TABLE ndila_place_gazetteer
    ADD CONSTRAINT chk_ndila_gazetteer_pair
    CHECK ((latitude IS NULL) = (longitude IS NULL));

-- Only these two tiers exist. A third would mean a new claim about precision that the renderer
-- does not know how to describe, and an undescribed precision is worse than none.
ALTER TABLE ndila_place_gazetteer
    DROP CONSTRAINT IF EXISTS chk_ndila_gazetteer_precision;
ALTER TABLE ndila_place_gazetteer
    ADD CONSTRAINT chk_ndila_gazetteer_precision
    CHECK (precision_tier IN ('ADDRESS', 'SUBURB_CENTROID'));

-- A row cannot be REVIEWED without a coordinate: reviewing means "I checked this point", and
-- there is nothing to check when it is null.
ALTER TABLE ndila_place_gazetteer
    DROP CONSTRAINT IF EXISTS chk_ndila_gazetteer_reviewed_has_point;
ALTER TABLE ndila_place_gazetteer
    ADD CONSTRAINT chk_ndila_gazetteer_reviewed_has_point
    CHECK (status <> 'REVIEWED' OR latitude IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_ndila_gazetteer_lookup
    ON ndila_place_gazetteer (tenant_id, locality_key, province_key)
    WHERE status = 'REVIEWED';

CREATE INDEX IF NOT EXISTS idx_ndila_gazetteer_worklist
    ON ndila_place_gazetteer (tenant_id, status, facility_count DESC);

-- The review queue gains the precision of whatever was proposed onto it, so a confirmation
-- carries the claim forward rather than flattening it.
ALTER TABLE ndila_geocode_review_queue
    ADD COLUMN IF NOT EXISTS proposed_precision VARCHAR(24);

ALTER TABLE ndila_geocode_review_queue
    DROP CONSTRAINT IF EXISTS chk_ndila_geocode_proposed_precision;
ALTER TABLE ndila_geocode_review_queue
    ADD CONSTRAINT chk_ndila_geocode_proposed_precision
    CHECK (proposed_precision IS NULL
           OR proposed_precision IN ('ADDRESS', 'SUBURB_CENTROID'));

-- Matching a queue row back to its location needs the owner key.
CREATE INDEX IF NOT EXISTS idx_ndila_locations_owner_entity
    ON ndila_locations (tenant_id, owner_entity_id);
