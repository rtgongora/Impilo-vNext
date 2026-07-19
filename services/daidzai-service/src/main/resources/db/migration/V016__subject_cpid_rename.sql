-- Identity Contract §7: the emergency/assistance/trauma subject is a
-- clinical-plane key — a CPID mapped by tshepo-identity, never a Health ID.
-- The columns have carried CPIDs since the identity-spine cutover; rename the
-- misleading name (rito V004 precedent).

ALTER TABLE daidzai.dai_emergency_request  RENAME COLUMN subject_health_id TO subject_cpid;
COMMENT ON COLUMN daidzai.dai_emergency_request.subject_cpid IS
    'Clinical subject CPID when known (protected; never a Health ID)';

ALTER TABLE daidzai.dai_emergency_incident RENAME COLUMN subject_health_id TO subject_cpid;
COMMENT ON COLUMN daidzai.dai_emergency_incident.subject_cpid IS
    'Clinical subject CPID when known (protected; never a Health ID)';

ALTER TABLE daidzai.dai_assistance_request RENAME COLUMN subject_health_id TO subject_cpid;
COMMENT ON COLUMN daidzai.dai_assistance_request.subject_cpid IS
    'Clinical subject CPID when known (protected; never a Health ID)';

ALTER TABLE daidzai.dai_trauma_episode     RENAME COLUMN subject_health_id TO subject_cpid;
COMMENT ON COLUMN daidzai.dai_trauma_episode.subject_cpid IS
    'Clinical subject CPID when known (protected; never a Health ID)';

-- Renaming a column does not rename the index; keep the name honest too.
ALTER INDEX daidzai.idx_dai_trauma_episode_subject RENAME TO idx_dai_trauma_episode_subject_cpid;
