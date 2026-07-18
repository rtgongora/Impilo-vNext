-- Identity Contract §7: clinical notes key the subject by CPID, never Health ID.
ALTER TABLE pct_clinical_notes RENAME COLUMN patient_health_id TO subject_cpid;
ALTER INDEX IF EXISTS idx_pct_clinical_notes_health RENAME TO idx_pct_clinical_notes_subject;
COMMENT ON COLUMN pct_clinical_notes.subject_cpid IS
    'Clinical subject CPID (mapped by tshepo-identity; never a Health ID)';
