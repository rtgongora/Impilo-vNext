-- =============================================================================
-- Ndila V004 — Shared geospatial place anchor (Place Journey Doctrine §6, D-L1)
--
-- One anchor represents ONE physical location / parcel / campus. Multiple
-- owner-keyed ndila_locations rows (a TUSO facility, an INDAWO site, a water
-- point on the same campus) may share one anchor — that is how "same physical
-- place, separately governed entities" is expressed WITHOUT merging registries.
-- Ndila stays geo-only: an anchor carries coordinates/boundary/jurisdiction,
-- never names, statuses or authority data. Anchor assignment is stewarded.
-- =============================================================================

CREATE TABLE IF NOT EXISTS ndila_place_anchor (
    id                  UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    centroid_latitude   NUMERIC(10,7)   NOT NULL,
    centroid_longitude  NUMERIC(10,7)   NOT NULL,
    boundary_geojson    JSONB,
    ward                VARCHAR(128),
    district            VARCHAR(128),
    province            VARCHAR(128),
    campus_label        VARCHAR(255),
    created_by          VARCHAR(80),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE ndila_place_anchor IS
    'Shared geospatial anchor (D-L1): one physical location/campus; referenced by owner-keyed ndila_locations rows across registries. Geo-only — never identity.';

ALTER TABLE ndila_locations
    ADD COLUMN IF NOT EXISTS anchor_id UUID REFERENCES ndila_place_anchor (id);

CREATE INDEX IF NOT EXISTS idx_ndila_locations_anchor ON ndila_locations (anchor_id)
    WHERE anchor_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_place_anchor_district ON ndila_place_anchor (tenant_id, province, district);
