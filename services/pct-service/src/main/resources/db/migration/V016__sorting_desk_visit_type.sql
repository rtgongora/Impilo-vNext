-- Sorting Desk + visit-type selection (decision D5).
-- The Sorting Desk is the explicit pre-queue step (after ARRIVED, before TRIAGE/QUEUE) that assigns a
-- visit-type to a journey. The visit-type is consumed by the Cadre Engine (C9) and routing.
-- This is a record of the sort decision, not a new journey state.

CREATE TABLE pct_sorting_records (
    id                BIGSERIAL       PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    journey_id        VARCHAR(26)     NOT NULL,
    visit_type        VARCHAR(64)     NOT NULL,
    context           VARCHAR(32)     NOT NULL,
    arrival_reason    TEXT,
    fast_track        BOOLEAN         NOT NULL DEFAULT FALSE,
    sorted_by         VARCHAR(255)    NOT NULL,
    correlation_id    UUID,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_sorting_records_journey ON pct_sorting_records (tenant_id, journey_id);
CREATE INDEX idx_pct_sorting_records_visit_type ON pct_sorting_records (tenant_id, visit_type);

COMMENT ON TABLE pct_sorting_records IS
    'Sorting Desk (D5) decisions: explicit pre-queue visit-type assignment per journey. Feeds the Cadre Engine (C9).';
COMMENT ON COLUMN pct_sorting_records.fast_track IS
    'True when the sort flags a fast-track / direct-to-care path (e.g. emergency or single-service visit).';
