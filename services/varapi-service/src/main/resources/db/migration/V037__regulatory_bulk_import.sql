-- =====================================================================================
-- varapi :: V037 — Regulatory bulk-import rail (ROM-U7, ingest spec)
-- STAGE -> MATCH -> REVIEW -> APPLY for a regulator's existing data (registered/licensed people +
-- their conditions of practice; council personnel). Staging is immutable; matching is against live
-- varapi truth; apply is review-gated, idempotent and GRANTS NO AUTHORITY (it writes register data
-- only — never a login, never an appointment's access). Mirrors the HPA importer discipline.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.regulatory_import_batch (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    council_id      BIGINT      REFERENCES varapi.councils(id),
    record_type     VARCHAR(32) NOT NULL,
    source_label    VARCHAR(255),
    filename        VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'STAGED',
    total_rows      INTEGER     NOT NULL DEFAULT 0,
    applied_rows    INTEGER     NOT NULL DEFAULT 0,
    uploaded_by     VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_import_record_type CHECK (record_type IN ('REGISTRANT', 'APPOINTMENT', 'LICENCE')),
    CONSTRAINT chk_import_batch_status CHECK (status IN ('STAGED', 'REVIEWED', 'APPLIED', 'ROLLED_BACK'))
);
CREATE INDEX IF NOT EXISTS idx_import_batch_tenant ON varapi.regulatory_import_batch(tenant_id, status);

CREATE TABLE IF NOT EXISTS varapi.regulatory_import_row (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    batch_id        BIGINT      NOT NULL REFERENCES varapi.regulatory_import_batch(id),
    row_index       INTEGER     NOT NULL,
    raw             JSONB       NOT NULL,
    match_status    VARCHAR(24) NOT NULL DEFAULT 'UNMATCHED',
    matched_ref     VARCHAR(128),
    review_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    apply_status    VARCHAR(20) NOT NULL DEFAULT 'NONE',
    message         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_import_match CHECK (match_status IN ('UNMATCHED','MATCHED_EXISTING','NEW','AMBIGUOUS','INVALID')),
    CONSTRAINT chk_import_review CHECK (review_status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT chk_import_apply CHECK (apply_status IN ('NONE','APPLIED','SKIPPED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_import_row_batch ON varapi.regulatory_import_row(batch_id);
