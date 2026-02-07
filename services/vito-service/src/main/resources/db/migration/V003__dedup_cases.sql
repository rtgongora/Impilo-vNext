-- VITO — Deduplication case management

CREATE TABLE vito.dedup_case (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    candidate_a     UUID         NOT NULL,
    candidate_b     UUID         NOT NULL,
    match_score     NUMERIC(5,4),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, MERGED, REJECTED, ESCALATED
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dedup_tenant_status ON vito.dedup_case (tenant_id, status);
