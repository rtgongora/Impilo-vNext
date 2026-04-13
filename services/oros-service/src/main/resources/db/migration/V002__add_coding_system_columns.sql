-- =============================================
-- Service : oros-service (Orders & Results Orchestration)
-- Flyway  : V002__add_coding_system_columns.sql
-- Purpose : Add coding system URI columns to support
--           international healthcare coding standards
--           (LOINC, SNOMED CT, ICD-11, etc.)
-- Doctrine: docs/doctrine/healthcare-coding-standards.md
-- =============================================

-- ── oros_order_items: add coding system context ────────────────────
-- Each order item code must now declare its terminology system.
-- Existing rows default to Impilo local coding until migrated.
ALTER TABLE oros_order_items
    ADD COLUMN coding_system   VARCHAR(255) NOT NULL DEFAULT 'http://impilo.gov.zw/coding',
    ADD COLUMN coding_version  VARCHAR(50),
    ADD COLUMN body_site_system VARCHAR(255),
    ADD COLUMN specimen_type_system VARCHAR(255);

COMMENT ON COLUMN oros_order_items.coding_system IS
    'Terminology system URI (e.g. http://loinc.org, http://snomed.info/sct)';
COMMENT ON COLUMN oros_order_items.coding_version IS
    'Version of the coding system used';
COMMENT ON COLUMN oros_order_items.body_site_system IS
    'Terminology system URI for body_site (typically http://snomed.info/sct)';
COMMENT ON COLUMN oros_order_items.specimen_type_system IS
    'Terminology system URI for specimen_type (typically http://snomed.info/sct)';

-- ── oros_catalog_items: add coding system context ─────────────────
ALTER TABLE oros_catalog_items
    ADD COLUMN coding_system   VARCHAR(255) NOT NULL DEFAULT 'http://impilo.gov.zw/coding',
    ADD COLUMN coding_version  VARCHAR(50);

COMMENT ON COLUMN oros_catalog_items.coding_system IS
    'Terminology system URI for the catalog item code';

-- ── oros_orders: add diagnosis coding on the order level ──────────
ALTER TABLE oros_orders
    ADD COLUMN primary_diagnosis_system VARCHAR(255),
    ADD COLUMN primary_diagnosis_code   VARCHAR(100),
    ADD COLUMN primary_diagnosis_display VARCHAR(500);

COMMENT ON COLUMN oros_orders.primary_diagnosis_system IS
    'ICD-11 system URI for the primary diagnosis driving this order';
COMMENT ON COLUMN oros_orders.primary_diagnosis_code IS
    'ICD-11 code for the primary diagnosis';
COMMENT ON COLUMN oros_orders.primary_diagnosis_display IS
    'Display name for the primary diagnosis';

-- ── Indexes ───────────────────────────────────────────────────────
CREATE INDEX idx_order_items_coding_system ON oros_order_items (coding_system);
CREATE INDEX idx_catalog_coding_system ON oros_catalog_items (coding_system);
