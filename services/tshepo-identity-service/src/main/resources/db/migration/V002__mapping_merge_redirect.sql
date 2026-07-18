-- Identity Contract §7.3: merged mappings are RETIRED with a redirect, never deleted.
-- When VITO merges two identities, the SubjectTranslationRelay marks the merged
-- (tenant, health_id) mapping RETIRED and records the survivor's CPID here so any
-- future resolution of the merged Health ID transparently returns the survivor CPID.
ALTER TABLE tshepo_identity.id_mapping
    ADD COLUMN merged_into_cpid UUID;

COMMENT ON COLUMN tshepo_identity.id_mapping.merged_into_cpid IS
    'Survivor CPID when mapping_status = RETIRED after an identity merge (redirect, never delete)';
