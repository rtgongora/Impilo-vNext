-- Supports the HPA importer's re-run guard (priorReviewPending), which looks up
-- prior review/conflict decisions by source_record_key across batches so an
-- ambiguous record already routed to a steward is never silently re-created on a
-- later import run. The existing uq_hmd_batch_key is (batch_id, source_record_key),
-- which cannot serve a source_record_key-only lookup.
CREATE INDEX IF NOT EXISTS idx_hmd_srk
    ON tuso.hpa_match_decision (source_record_key);
