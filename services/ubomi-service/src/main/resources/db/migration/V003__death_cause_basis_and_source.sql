-- Community / Field Death Pathway — CRVS half (docs/clinical/community-field-death-pathway.md).
-- Ubomi OWNS CRVS. A death may be registered with a PROBABLE cause (verbal autopsy / field
-- investigation) for a person who never entered a facility — but that probable cause must never be
-- counted as a medically certified one in mortality intelligence. This records the certainty CLASS
-- (cause_of_death_basis) alongside the origin (source_context) and where the body went
-- (body_disposition_context), so the national CRVS/mortality dataset can separate certified from
-- probable causes. We never forge a REGISTERED state; civil_reg_number is still only set by a real
-- Registrar General response.
ALTER TABLE ubomi.death_notification
    ADD COLUMN IF NOT EXISTS cause_of_death_basis     VARCHAR(32),
        -- MEDICALLY_CERTIFIED|POST_MORTEM_CERTIFIED|VERBAL_AUTOPSY_PROBABLE
        -- |FIELD_INVESTIGATION_PROBABLE|MEDICO_LEGAL_PENDING|UNKNOWN_UNCERTIFIED
    ADD COLUMN IF NOT EXISTS source_context           VARCHAR(40),
        -- FACILITY|BROUGHT_IN_DEAD|COMMUNITY_HOME|COMMUNITY_FIELD|OUTREACH_FIELD_OPS|DISASTER_SITE
        -- |ISOLATION_FIELD_SITE|CUSTODY|UNKNOWN_LOCATION
    ADD COLUMN IF NOT EXISTS body_disposition_context VARCHAR(40);
        -- BROUGHT_TO_FACILITY|NOT_BROUGHT_TO_FACILITY|FIELD_SAFE_BURIAL|COMMUNITY_RELEASE
        -- |POLICE_CUSTODY|FUNERAL_DIRECTOR_DIRECT|TRANSFERRED_TO_MORTUARY|TRANSFERRED_TO_POST_MORTEM_SITE

-- place_of_death_type is no longer limited to facility-ish values; community/field deaths are valid.
COMMENT ON COLUMN ubomi.death_notification.place_of_death_type IS
    'FACILITY|HOME|COMMUNITY|FIELD|OUTREACH|DISASTER_SITE|ISOLATION_SITE|DOA|TRANSIT|CUSTODY|OTHER';

-- Backfill basis for existing rows: a CERTIFIED/REGISTERED notification is medically certified.
UPDATE ubomi.death_notification
   SET cause_of_death_basis = 'MEDICALLY_CERTIFIED'
 WHERE cause_of_death_basis IS NULL
   AND status IN ('CERTIFIED', 'REGISTERED');

CREATE INDEX IF NOT EXISTS idx_death_cod_basis
    ON ubomi.death_notification (tenant_id, cause_of_death_basis);
