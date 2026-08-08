-- ============================================================
-- PCT
-- V502: encounter_ref becomes a real business key
--
-- pct_encounters has two identifiers and they are used by different planes:
--
--   id           BIGSERIAL  -- what the REST surface is keyed on (/v1/encounters/{id})
--   encounter_ref UUID      -- what every Kafka payload is keyed on, and what leaves this service
--
-- Only the UUID travels. oros carries it as orders.encounter_ref, inpatient as
-- admissions.encounter_id, daidzai as dai_emergency_incident.pct_encounter_ref, and BUTANO now
-- writes it as the FHIR Encounter's business identifier under
-- https://impilo.gov.zw/pct/encounter-id, which is what makes a clinical resource in any of those
-- services resolvable against the shared health record.
--
-- It carried only a plain index (idx_pct_encounters_encounter_ref, V002). A cross-service business
-- key with no uniqueness guarantee is one collision away from two facilities' visits merging into
-- one FHIR Encounter, silently, with no way to tell afterwards which resources belonged to which.
-- The upsert BUTANO performs is keyed on exactly this value.
--
-- Measured in impilo-full-preview before writing this: 6 rows, 6 distinct encounter_refs, 0 nulls.
-- EncounterService.startEncounter is the only construction path for EncounterEntity and it always
-- assigns UUID.randomUUID(), so NOT NULL is a statement of what is already true rather than a new
-- restriction.
--
-- The plain index is replaced by the unique one; keeping both would be a second index on the same
-- column doing no additional work.
-- ============================================================

ALTER TABLE pct_encounters
    ALTER COLUMN encounter_ref SET NOT NULL;

DROP INDEX IF EXISTS idx_pct_encounters_encounter_ref;

CREATE UNIQUE INDEX idx_pct_encounters_encounter_ref
    ON pct_encounters (encounter_ref);

COMMENT ON COLUMN pct_encounters.encounter_ref IS
    'The cross-service business key for this facility visit. Every Kafka payload is keyed on it, '
    'oros/inpatient/daidzai carry it, and BUTANO writes it as the FHIR Encounter identifier under '
    'https://impilo.gov.zw/pct/encounter-id. Unique and non-null: this is the value other planes '
    'resolve against. pct_encounters.id is internal to this service''s REST surface.';
