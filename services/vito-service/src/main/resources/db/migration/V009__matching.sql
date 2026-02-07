-- =============================================================================
-- VITO — Probabilistic Matching Engine Results
-- WHO-recommended matching: stores match results for resolution workflow.
-- Supports both internal matching and OpenCR FHIR $match results.
-- =============================================================================

CREATE TABLE vito.match_result (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    source_health_id    UUID         NOT NULL,
    candidate_health_id UUID         NOT NULL,
    match_score         NUMERIC(5,4) NOT NULL,           -- 0.0000 to 1.0000
    match_algorithm     VARCHAR(50)  NOT NULL,           -- PROBABILISTIC, DETERMINISTIC, OPENCR
    match_fields        JSONB        NOT NULL,           -- which fields contributed to score
    disposition         VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
                                                         -- PENDING, AUTO_LINKED, MANUAL_LINKED,
                                                         -- REJECTED, DEFERRED
    resolved_by         VARCHAR(255),
    resolved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_match_pair UNIQUE (tenant_id, source_health_id, candidate_health_id, match_algorithm)
);

CREATE INDEX idx_match_source    ON vito.match_result (tenant_id, source_health_id, disposition);
CREATE INDEX idx_match_candidate ON vito.match_result (tenant_id, candidate_health_id, disposition);
CREATE INDEX idx_match_pending   ON vito.match_result (tenant_id, disposition)
    WHERE disposition = 'PENDING';
