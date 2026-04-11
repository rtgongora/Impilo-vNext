-- =============================================
-- Service : pharmacy-service
-- Flyway  : V002__add_coding_system_columns.sql
-- Purpose : Add coding system URI columns for ATC/SNOMED CT
--           drug classification per Healthcare Coding Standards doctrine
-- Doctrine: docs/doctrine/healthcare-coding-standards.md
-- =============================================

-- ── rx_dispense_items: add coding system context ──────────────────
-- Drug codes must now declare their terminology system (ATC, SNOMED CT, etc.)
ALTER TABLE rx_dispense_items
    ADD COLUMN drug_coding_system VARCHAR(255) NOT NULL DEFAULT 'http://www.whocc.no/atc',
    ADD COLUMN drug_coding_version VARCHAR(50);

COMMENT ON COLUMN rx_dispense_items.drug_coding_system IS
    'Terminology system URI for drug_code (default: ATC)';

-- ── rx_stock_movements: add coding system context ─────────────────
ALTER TABLE rx_stock_movements
    ADD COLUMN item_coding_system VARCHAR(255) NOT NULL DEFAULT 'http://www.whocc.no/atc',
    ADD COLUMN item_coding_version VARCHAR(50);

COMMENT ON COLUMN rx_stock_movements.item_coding_system IS
    'Terminology system URI for item_code (default: ATC)';

-- ── rx_formulary: add coding system context ───────────────────────
ALTER TABLE rx_formulary
    ADD COLUMN drug_coding_system VARCHAR(255) NOT NULL DEFAULT 'http://www.whocc.no/atc',
    ADD COLUMN drug_coding_version VARCHAR(50),
    ADD COLUMN atc_code           VARCHAR(10),
    ADD COLUMN snomed_code        VARCHAR(20);

COMMENT ON COLUMN rx_formulary.drug_coding_system IS
    'Primary terminology system URI for drug_code (default: ATC)';
COMMENT ON COLUMN rx_formulary.atc_code IS
    'ATC classification code (may differ from drug_code if drug_code uses a local system)';
COMMENT ON COLUMN rx_formulary.snomed_code IS
    'SNOMED CT clinical drug concept ID for cross-reference';

-- ── rx_substitution_rules: add coding system context ──────────────
ALTER TABLE rx_substitution_rules
    ADD COLUMN source_coding_system VARCHAR(255) NOT NULL DEFAULT 'http://www.whocc.no/atc',
    ADD COLUMN target_coding_system VARCHAR(255) NOT NULL DEFAULT 'http://www.whocc.no/atc';

COMMENT ON COLUMN rx_substitution_rules.source_coding_system IS
    'Terminology system URI for source_code';
COMMENT ON COLUMN rx_substitution_rules.target_coding_system IS
    'Terminology system URI for target_code';

-- ── Indexes ───────────────────────────────────────────────────────
CREATE INDEX idx_rx_formulary_atc ON rx_formulary (atc_code) WHERE atc_code IS NOT NULL;
CREATE INDEX idx_rx_formulary_snomed ON rx_formulary (snomed_code) WHERE snomed_code IS NOT NULL;
