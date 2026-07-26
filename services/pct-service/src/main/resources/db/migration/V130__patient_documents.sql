-- Patient documents — the patient-anchored clinical document index.
--
-- Closes the /v1/records vertical found by the route-contract guard: the experience layer has
-- been creating and listing "patient records" (document metadata — a scanned referral letter, an
-- uploaded discharge summary, a lab report PDF) against pct paths that never existed, so every
-- clinical-documents list rendered empty and every create was a 404 dressed as a workflow. The
-- binary itself lives in document-service (MinIO objects; SoR for the bytes) and stays there —
-- this table is the CLINICAL INDEX of those objects: which person, which care context, what kind
-- of document, who added it. Neither store duplicates the other: document-service knows nothing
-- of patients, and pct holds no bytes.
--
-- Lease: docs/registry/iatg-completion-clinical-core-leases.md (pct V130–V134).
--
-- CC-5: the document resolves to the person's care continuum via subject_cpid and, when captured
-- in a care context, the journey/encounter it belongs to.

CREATE TABLE pct.pct_patient_documents (
    document_id          UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    subject_cpid         VARCHAR(64) NOT NULL,
    journey_id           VARCHAR(64),
    encounter_id         VARCHAR(64),
    facility_id          UUID,

    -- What kind of document. Open vocabulary, uppercased at write; the experience layer's
    -- known types today are CLINICAL_NOTE | DISCHARGE_SUMMARY | LAB_REPORT | IMAGING.
    document_type        VARCHAR(48) NOT NULL,
    title                VARCHAR(255) NOT NULL,
    description          TEXT,

    -- The binary, by reference. storage_key addresses the object in document-service;
    -- document_object_id is its object id when the caller registered one, which is what makes
    -- signed-URL enrichment possible without guessing.
    mime_type            VARCHAR(128),
    file_size_bytes      BIGINT,
    storage_key          VARCHAR(512) NOT NULL,
    document_object_id   UUID,

    uploaded_by          VARCHAR(128),
    recorded_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Documents are never deleted: a withdrawn document states that it was withdrawn.
    status               VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
    -- ACTIVE | SUPERSEDED | ENTERED_IN_ERROR
);

CREATE INDEX idx_pct_patient_documents_subject
    ON pct.pct_patient_documents(tenant_id, subject_cpid, recorded_at DESC);
CREATE INDEX idx_pct_patient_documents_subject_type
    ON pct.pct_patient_documents(tenant_id, subject_cpid, document_type);
CREATE INDEX idx_pct_patient_documents_encounter
    ON pct.pct_patient_documents(tenant_id, encounter_id)
    WHERE encounter_id IS NOT NULL;
