-- inpatient-service V303 — Clinical Procedures Pipeline P13 (§24 interoperability, specimen half).
--
-- Tracks the returned FHIR Specimen resource id once ButanoProcedureClient.writeSpecimen succeeds
-- — the same audit-trail pattern already used for procedure_specimen.butano_document_ref (the
-- PATHOLOGY DocumentReference, set by attachPathology) and procedure_note.butano_document_ref
-- (the operative-note DocumentReference). This is a DIFFERENT FHIR resource (Specimen, not
-- DocumentReference) recording a DIFFERENT fact (what was collected, not the eventual report on
-- it), so it gets its own column rather than overloading butano_document_ref's existing meaning.

ALTER TABLE inpatient.procedure_specimen
    ADD COLUMN IF NOT EXISTS fhir_specimen_ref VARCHAR(256);
