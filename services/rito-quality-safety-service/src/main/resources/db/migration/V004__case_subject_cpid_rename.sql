-- Identity Contract §7: the case subject is a clinical-plane key — a CPID mapped
-- by tshepo-identity, never a Health ID. Rename the misleading column.
ALTER TABLE rito.rit_case RENAME COLUMN subject_health_id TO subject_cpid;
COMMENT ON COLUMN rito.rit_case.subject_cpid IS
    'Clinical subject CPID the case concerns (protected; never a Health ID)';
