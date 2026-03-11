-- ============================================================================
-- indawo-service V002 — Site & Premises Registry tables
-- Ring 0 registry for site/premises identity and topology (distinct from TUSO)
-- ============================================================================

-- Sites — the core site/premises identity
CREATE TABLE ind_sites (
    site_id         UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    type            VARCHAR(64)     NOT NULL,  -- CLINIC, HOSPITAL, WAREHOUSE, OFFICE, etc.
    address_json    JSONB,                      -- structured address
    geo_json        JSONB,                      -- GeoJSON point/polygon
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    version         INT             NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ind_sites_tenant ON ind_sites (tenant_id);
CREATE INDEX idx_ind_sites_status ON ind_sites (status);
CREATE INDEX idx_ind_sites_type ON ind_sites (type);

-- Site Relationships — parent/child topology
CREATE TABLE ind_site_relationships (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    parent_site_id  UUID            NOT NULL REFERENCES ind_sites(site_id),
    child_site_id   UUID            NOT NULL REFERENCES ind_sites(site_id),
    rel_type        VARCHAR(64)     NOT NULL,  -- PARENT_OF, DEPARTMENT_OF, WING_OF, etc.
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ind_site_rel UNIQUE (parent_site_id, child_site_id, rel_type)
);

CREATE INDEX idx_ind_site_rel_parent ON ind_site_relationships (parent_site_id);
CREATE INDEX idx_ind_site_rel_child ON ind_site_relationships (child_site_id);

-- Add partition_key to event outbox for meta.partition_key support
ALTER TABLE ind_event_outbox ADD COLUMN IF NOT EXISTS partition_key VARCHAR(255);
