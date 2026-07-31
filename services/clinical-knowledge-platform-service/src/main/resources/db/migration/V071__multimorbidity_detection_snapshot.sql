-- =============================================================================
-- V071 — multimorbidity detection snapshot (Adult Medicine lease V071–V080)
--
-- The §9 engine is otherwise stateless per assess: it emits DETECTED findings and
-- nothing when a finding stops. Without a prior-open ledger there is no way to emit
-- a resolution without guessing from silence — and silence must never retire an
-- issue (handover Wave 2; completion-register §19).
--
-- One row per (tenant, subject, code). Status flips OPEN ↔ RETIRED; re-detection
-- reopens the same row. Rows are written only when an assess names a subject CPID.
-- =============================================================================

CREATE TABLE clinical.multimorbidity_detection_snapshot (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(128)  NOT NULL,
    subject_cpid     VARCHAR(64)   NOT NULL,
    code             VARCHAR(128)  NOT NULL,
    detection_key    VARCHAR(128)  NOT NULL,
    severity         VARCHAR(32),
    content_version  VARCHAR(64),
    detected_at      TIMESTAMPTZ   NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_mm_detection_snapshot_subject_code
        UNIQUE (tenant_id, subject_cpid, code),
    CONSTRAINT chk_mm_detection_snapshot_status
        CHECK (status IN ('OPEN', 'RETIRED'))
);

CREATE INDEX idx_mm_detection_snapshot_open
    ON clinical.multimorbidity_detection_snapshot (tenant_id, subject_cpid)
    WHERE status = 'OPEN';
