-- Cross-program link (theatre ↔ trauma): a surgical procedure episode that ORIGINATES from a
-- trauma episode carries the daidzai-minted trauma_episode_id, giving ONE coherent trauma↔theatre
-- episode (no parallel surgery record — single source of truth). Nullable: null for elective/
-- non-trauma cases. Set from the X-Trauma-Episode-ID header on emergency-surgery / obstetric-
-- emergency intake (consumption logic lands with Wave 5b once the daidzai mint contract is live).
-- Bare additive column landed ahead of the trauma lane's V035 to keep inpatient migration ordering
-- clean (theatre lane owns procedure_* per the class-level co-edit split, docs/registry/iatg-trauma-leases.md).
ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS trauma_episode_id UUID;

CREATE INDEX IF NOT EXISTS idx_procedure_episode_trauma
    ON inpatient.procedure_episode (tenant_id, trauma_episode_id)
    WHERE trauma_episode_id IS NOT NULL;
