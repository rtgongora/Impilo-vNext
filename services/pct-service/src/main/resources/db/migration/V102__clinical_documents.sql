-- Clinical document index (PCT).
--
-- Completing a capability the estate has always half-had. `experience-bff` exposes clinical
-- documents at /internal/v1/clinical-documents and a citizen-facing records view, both calling
-- pct-service at /v1/records and /v1/patient/{cpid}/records — paths pct-service has never served.
-- Two live callers, one of them a person fetching their own record.
--
-- WHY THIS TABLE RATHER THAN A REPOINT. The obvious fix is to point the client at whatever already
-- serves it, as the problem list was repointed from /v1/conditions to /v1/problems. That does not
-- work here, because nothing serves it. The capability is genuinely split in two and only one half
-- exists:
--
--   * document-service owns the OBJECT — bytes, mime type, size, storage key, hash, OCR, signed
--     URLs — at /v1/internal/objects. That half is real and well built.
--   * Nothing owns the CLINICAL INDEX: which document belongs to which patient, under which
--     encounter, of what clinical type. There is no per-patient, per-encounter view anywhere.
--
-- So this is not a routing mistake, it is a missing system of record, and under the product-owner
-- rule (we complete functionality rather than delete it to hide incompleteness) it is completed.
-- PCT owns it because a clinical document attaches to a subject and an encounter, and PCT owns the
-- encounter anchor (care-continuum-doctrine CC-5). document-service keeps the bytes; this table
-- points at them. Neither duplicates the other.
--
-- The column shape deliberately matches the orphaned `clinical_documents` table in the BFF's own
-- V6 migration, which the UI was built against but which has never been created — the BFF has no
-- datasource outside the CI test profile. Matching it means the experience layer keeps working
-- against the contract it already expects. Two differences, both deliberate:
--
--   * `subject_cpid` rather than a patients(id) foreign key. The SHR boundary is CPID-only; PCT
--     does not hold a patient row to reference.
--   * `document_object_id` is a first-class column rather than being implied by `storage_key`.
--     The object is document-service's identifier and is what a reader must use to fetch the
--     bytes; a storage key is an implementation detail of wherever the object currently lives.

CREATE TABLE pct_clinical_documents (
    document_id        UUID            PRIMARY KEY,
    tenant_id          UUID            NOT NULL,
    subject_cpid       VARCHAR(128)    NOT NULL,
    journey_id         VARCHAR(26),
    encounter_id       VARCHAR(64),

    document_type      VARCHAR(64)     NOT NULL DEFAULT 'CLINICAL_NOTE',
    title              VARCHAR(512)    NOT NULL,
    description        TEXT,

    -- The object in document-service. Nullable because metadata may be indexed before the upload
    -- completes; a row with no object is a document that is referenced but not yet retrievable,
    -- which is a state a reader must be able to tell from one that is.
    document_object_id UUID,
    mime_type          VARCHAR(128),
    file_size          BIGINT,
    storage_key        VARCHAR(512),

    status             VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    uploaded_by        VARCHAR(255)    NOT NULL,
    recorded_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_clinical_documents_status_check CHECK (status IN (
        'ACTIVE',
        'SUPERSEDED',
        'WITHDRAWN'      -- withdrawn in error; retained because removing a document silently is
                         -- indistinguishable from it never having existed
    )),
    -- A negative or zero file size is not a smaller file, it is a broken record.
    CONSTRAINT pct_clinical_documents_size_check CHECK (file_size IS NULL OR file_size > 0)
);

CREATE INDEX idx_pct_clinical_documents_subject
    ON pct_clinical_documents (tenant_id, subject_cpid, recorded_at DESC);
CREATE INDEX idx_pct_clinical_documents_encounter
    ON pct_clinical_documents (tenant_id, encounter_id)
    WHERE encounter_id IS NOT NULL;
CREATE INDEX idx_pct_clinical_documents_type
    ON pct_clinical_documents (tenant_id, subject_cpid, document_type);

COMMENT ON TABLE pct_clinical_documents IS
    'Clinical index of documents attached to a subject and encounter. document-service owns the '
    'object (bytes, mime, storage, OCR); this owns which document belongs to whose care and when. '
    'Neither duplicates the other.';
COMMENT ON COLUMN pct_clinical_documents.document_object_id IS
    'The object in document-service. NULL means indexed but not yet retrievable — distinguishable '
    'from a document that is.';
