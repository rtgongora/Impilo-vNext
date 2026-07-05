-- Imaging study reconciliation exceptions queue.
--
-- Every ambiguous or manual-path study raises an explicit exception row instead of being
-- silently attached to a patient/order. Resolution is auditable: who, when, action, note.

CREATE TABLE IF NOT EXISTS pacs.imaging_study_exception (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    study_id          BIGINT       REFERENCES pacs.imaging_study (id),

    -- STUDY_WITHOUT_ORDER | MISSING_ACCESSION | MANUAL_IMPORT_REVIEW | DIGITISED_REVIEW
    -- | DOWNTIME_CAPTURE_REVIEW | PATIENT_MISMATCH | FACILITY_MISMATCH | MODALITY_MISMATCH
    -- | DUPLICATE_PATIENT | REPORT_WITHOUT_STUDY | STUDY_WITHOUT_REPORT
    exception_type    VARCHAR(64)  NOT NULL,

    -- OPEN | RESOLVED | DISMISSED
    status            VARCHAR(32)  NOT NULL DEFAULT 'OPEN',

    detail            TEXT,

    raised_by         VARCHAR(128),
    raised_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    resolved_by       VARCHAR(128),
    resolved_at       TIMESTAMPTZ,
    -- e.g. CORRELATED_TO_ORDER | PATIENT_CONFIRMED | REJECTED_WRONG_PATIENT | DISMISSED_DUPLICATE
    resolution_action VARCHAR(64),
    resolution_note   TEXT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_imaging_exception_tenant_status
    ON pacs.imaging_study_exception (tenant_id, status, raised_at DESC);

CREATE INDEX IF NOT EXISTS idx_imaging_exception_study
    ON pacs.imaging_study_exception (study_id);

COMMENT ON TABLE pacs.imaging_study_exception IS
    'Reconciliation exceptions for incoming imaging studies; resolution is auditable and never silent.';
