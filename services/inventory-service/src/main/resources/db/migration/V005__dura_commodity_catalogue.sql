-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V005__dura_commodity_catalogue.sql
-- Prefix  : inv_
--
-- Dura Wave 2: rich commodity catalogue foundation.
--   1. inv_commodity_categories — category hierarchy
--   2. inv_items — additive Dura catalogue attributes
--
-- Additive only. No existing column is altered or dropped.
-- =============================================

-- ─────────────────────────────────────────────
-- 1. inv_commodity_categories
--    Hierarchical classification of commodities
--    (medicines, vaccines, test kits, reagents,
--    sundries, devices, PPE, nutrition, gases, etc.)
-- ─────────────────────────────────────────────
CREATE TABLE inv_commodity_categories (
    category_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL,
    code               VARCHAR(50)   NOT NULL,
    name               VARCHAR(255)  NOT NULL,
    parent_category_id UUID          REFERENCES inv_commodity_categories(category_id),
    programme_area     VARCHAR(100),
    description        VARCHAR(500),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_commodity_categories_tenant  ON inv_commodity_categories(tenant_id);
CREATE INDEX idx_commodity_categories_parent  ON inv_commodity_categories(parent_category_id) WHERE parent_category_id IS NOT NULL;
CREATE INDEX idx_commodity_categories_prog     ON inv_commodity_categories(tenant_id, programme_area) WHERE programme_area IS NOT NULL;

-- ─────────────────────────────────────────────
-- 2. inv_items — Dura catalogue enrichment
--    Adds the commodity attributes Dura needs to
--    drive stock-aware clinical decisions: pack /
--    dispensing units, programme area, cold-chain,
--    tracking requirements, regulatory status, GTIN
--    and external code mappings.
-- ─────────────────────────────────────────────
ALTER TABLE inv_items ADD COLUMN category_id              UUID         REFERENCES inv_commodity_categories(category_id);
ALTER TABLE inv_items ADD COLUMN subcategory              VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN generic_name             VARCHAR(255);
ALTER TABLE inv_items ADD COLUMN brand_name               VARCHAR(255);
ALTER TABLE inv_items ADD COLUMN form                     VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN strength                 VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN pack_size                VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN stocking_unit            VARCHAR(50);
ALTER TABLE inv_items ADD COLUMN dispensing_unit          VARCHAR(50);
ALTER TABLE inv_items ADD COLUMN programme_area           VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN cold_chain_required      BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE inv_items ADD COLUMN temperature_min          DOUBLE PRECISION;
ALTER TABLE inv_items ADD COLUMN temperature_max          DOUBLE PRECISION;
ALTER TABLE inv_items ADD COLUMN batch_tracking_required  BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE inv_items ADD COLUMN expiry_tracking_required BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE inv_items ADD COLUMN serial_tracking_required BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE inv_items ADD COLUMN substitution_group       VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN essential_medicine       BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE inv_items ADD COLUMN regulatory_status        VARCHAR(50);
ALTER TABLE inv_items ADD COLUMN gtin                     VARCHAR(100);
ALTER TABLE inv_items ADD COLUMN external_codes           JSONB;

CREATE INDEX idx_items_category        ON inv_items(category_id) WHERE category_id IS NOT NULL;
CREATE INDEX idx_items_programme       ON inv_items(tenant_id, programme_area) WHERE programme_area IS NOT NULL;
CREATE INDEX idx_items_gtin            ON inv_items(gtin) WHERE gtin IS NOT NULL;
CREATE INDEX idx_items_cold_chain      ON inv_items(tenant_id) WHERE cold_chain_required = TRUE;
CREATE INDEX idx_items_substitution    ON inv_items(tenant_id, substitution_group) WHERE substitution_group IS NOT NULL;
