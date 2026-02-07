-- =====================================================================
-- Flyway V002: Full-text search indexes for MSIKA catalog items
-- =====================================================================

-- Add tsvector column for full-text search on display_name + synonyms
ALTER TABLE msika_catalog_items
    ADD COLUMN search_vector TSVECTOR;

-- Populate existing rows
UPDATE msika_catalog_items
SET search_vector = to_tsvector('english',
    COALESCE(display_name, '') || ' ' ||
    COALESCE(description, '') || ' ' ||
    COALESCE(array_to_string(synonyms, ' '), '')
);

-- GIN index for fast FTS queries
CREATE INDEX idx_msika_items_fts ON msika_catalog_items USING GIN (search_vector);

-- Trigger to auto-update search_vector on INSERT/UPDATE
CREATE OR REPLACE FUNCTION msika_items_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        COALESCE(NEW.display_name, '') || ' ' ||
        COALESCE(NEW.description, '') || ' ' ||
        COALESCE(array_to_string(NEW.synonyms, ' '), '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_msika_items_search
    BEFORE INSERT OR UPDATE OF display_name, description, synonyms
    ON msika_catalog_items
    FOR EACH ROW
    EXECUTE FUNCTION msika_items_search_trigger();

-- Unique partial index for barcode within tenant scope
CREATE UNIQUE INDEX uq_msika_product_barcode_tenant
    ON msika_product_details (barcode)
    WHERE barcode IS NOT NULL;

-- Index for external_code lookups via metadata
CREATE INDEX idx_msika_items_external_code
    ON msika_catalog_items ((metadata->>'external_code'))
    WHERE metadata->>'external_code' IS NOT NULL;
