-- =============================================================================
-- Indawo V010 — Cross-registry typed place links (Place Journey Doctrine §6, D-L2)
--
-- The single home for typed Tuso<->Indawo relationships. One table, one writer
-- (Indawo), served both directions; Tuso consumes read-only. Refs are the
-- canonical UUIDs of each registry (tuso.facility.facility_uuid /
-- ind_sites.site_id) — never the numeric surrogate ids, never derived from one
-- another. Links carry validity, provenance and the steward actor; linked
-- records keep separate authorities, licences, inspections and lifecycles.
-- =============================================================================

CREATE TABLE IF NOT EXISTS ind_place_links (
    id              UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    left_ref_type   VARCHAR(20)     NOT NULL,
    left_ref_id     UUID            NOT NULL,
    right_ref_type  VARCHAR(20)     NOT NULL,
    right_ref_id    UUID            NOT NULL,
    link_type       VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | ENDED
    valid_from      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    valid_to        TIMESTAMPTZ,
    provenance      VARCHAR(255),
    steward_actor   VARCHAR(80),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_place_link_ref_types CHECK (
        left_ref_type  IN ('TUSO_FACILITY', 'INDAWO_SITE') AND
        right_ref_type IN ('TUSO_FACILITY', 'INDAWO_SITE')),
    CONSTRAINT chk_place_link_type CHECK (link_type IN (
        'LOCATED_WITHIN', 'SAME_CAMPUS_AS', 'CONTAINS', 'HOSTS_SERVICE_POINT',
        'TEMPORARY_SERVICE_AT', 'REGULATED_COMPONENT_OF', 'REPLACED_BY', 'SERVES')),
    CONSTRAINT chk_place_link_no_self CHECK (
        NOT (left_ref_type = right_ref_type AND left_ref_id = right_ref_id))
);

COMMENT ON TABLE ind_place_links IS
    'Typed cross-registry place relationships (Place Journey Doctrine §6). Single-writer: Indawo. Refs = canonical UUIDs (tuso facility_uuid / indawo site_id).';

-- One ACTIVE link per (pair, type); ended links remain as history.
CREATE UNIQUE INDEX IF NOT EXISTS ux_place_link_active
    ON ind_place_links (tenant_id, left_ref_type, left_ref_id, right_ref_type, right_ref_id, link_type)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_place_link_left  ON ind_place_links (tenant_id, left_ref_type, left_ref_id);
CREATE INDEX IF NOT EXISTS idx_place_link_right ON ind_place_links (tenant_id, right_ref_type, right_ref_id);

-- Tighten the legacy intra-registry relationship vocabulary (free-string rot).
-- NOT VALID: existing rows are not re-checked (unknown legacy strings survive as
-- history); every NEW write must use the closed vocabulary.
ALTER TABLE ind_site_relationships
    ADD CONSTRAINT chk_site_rel_type CHECK (rel_type IN (
        'PARENT_OF', 'DEPARTMENT_OF', 'WING_OF', 'CONTAINS', 'LOCATED_WITHIN')) NOT VALID;
