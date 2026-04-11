-- =============================================
-- Service : pacs-adapter-service (PACS & DICOM Gateway)
-- Flyway  : V002__add_coding_system_columns.sql
-- Purpose : Add DICOM and SNOMED CT coding context to imaging studies
--           per Healthcare Coding Standards doctrine
-- Doctrine: docs/doctrine/healthcare-coding-standards.md
-- =============================================

-- ── imaging_study: add structured coding ──────────────────────────
ALTER TABLE imaging_study
    ADD COLUMN modality_system       VARCHAR(255) DEFAULT 'http://dicom.nema.org/resources/ontology/DCM',
    ADD COLUMN body_part_system      VARCHAR(255),
    ADD COLUMN body_part_code        VARCHAR(100),
    ADD COLUMN body_part_display     VARCHAR(500),
    ADD COLUMN procedure_system      VARCHAR(255),
    ADD COLUMN procedure_code        VARCHAR(100),
    ADD COLUMN procedure_display     VARCHAR(500),
    ADD COLUMN reason_system         VARCHAR(255),
    ADD COLUMN reason_code           VARCHAR(100),
    ADD COLUMN reason_display        VARCHAR(500);

COMMENT ON COLUMN imaging_study.modality_system IS
    'DICOM system URI for modality codes (default: DCM ontology)';
COMMENT ON COLUMN imaging_study.body_part_system IS
    'SNOMED CT system URI for body part examined';
COMMENT ON COLUMN imaging_study.procedure_system IS
    'SNOMED CT or LOINC system URI for the imaging procedure';
COMMENT ON COLUMN imaging_study.reason_system IS
    'ICD-11 system URI for the clinical reason/indication';

-- ── Indexes ───────────────────────────────────────────────────────
CREATE INDEX idx_imaging_body_part ON imaging_study (body_part_code)
    WHERE body_part_code IS NOT NULL;
CREATE INDEX idx_imaging_procedure ON imaging_study (procedure_code)
    WHERE procedure_code IS NOT NULL;
