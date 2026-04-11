-- =============================================================================
-- Align seeded national guideline row with repository EDLIZ 2025 reference bundle
-- (see docs/reference/edliz-2025/README.md and ADR/programme records for provenance).
-- SHA-256 of bundled PDF: 9b9b52e22f7d6aaa2e0dbcdbf40f92c90d592eeaa2cd5f83ddafc33703b24581
-- =============================================================================

UPDATE clinical.source_documents
SET
    title = 'EDLIZ 2025 — Essential Medicines and Standard Treatment Guidelines (final for circulation, national reference copy)',
    source_type = 'NATIONAL_GUIDELINE',
    file_reference = 'docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf',
    content_hash = '9b9b52e22f7d6aaa2e0dbcdbf40f92c90d592eeaa2cd5f83ddafc33703b24581',
    ingestion_status = 'REFERENCE_BUNDLED',
    updated_at = NOW()
WHERE id = 'a0000001-0001-4001-8001-000000000001';
