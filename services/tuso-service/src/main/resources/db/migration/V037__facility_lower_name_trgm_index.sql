-- Index-backed trigram matching for the D-L5 facility matcher.
--
-- FacilityMatchService scores duplicates on similarity(lower(f.name), :q). The
-- existing V002 index is gin(name gin_trgm_ops) — on the RAW name — so the
-- lowercased match could not use it and fell back to a sequential similarity
-- scan of every tenant facility (O(n) per lookup; O(n^2) across a bulk import).
-- A functional GIN index on lower(name) lets the % operator prefilter via the
-- index while the >= threshold keeps the exact result set unchanged.
CREATE INDEX IF NOT EXISTS idx_facility_lower_name_trgm
    ON tuso.facility USING gin (lower(name) gin_trgm_ops);
