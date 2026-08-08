-- =============================================================================
-- V402 — Publish the six seeded vocabularies that were left in review state
--
-- Measured in impilo-full-preview, 2026-08-08:
--
--   tenant                                 status      count
--   00000000-0000-0000-0000-000000000001   DRAFT           7   (ObservationDefinitions)
--   00000000-0000-0000-0000-000000000001   PUBLISHED      30
--   00000000-0000-4000-8000-000000000001   ACTIVE          6   <-- these
--   00000000-0000-4000-8000-000000000001   PUBLISHED       1
--
-- ArtifactStatus documents ACTIVE as "in review", between DRAFT and PUBLISHED, and
-- ArtifactResolutionService.resolveCurrent filters to PUBLISHED. So these six have been
-- unresolvable since they were seeded — including ICD10ZW, EDLIZ, LabTestsZW and
-- ProviderSpecialtiesZW, which are four of the only vocabularies in this service holding
-- more than a handful of real codes, and two of the only three ValueSets that enumerate
-- any concepts at all.
--
-- They are not in review. scripts/seed/06-seed-zibo.sql hard-codes 'ACTIVE' as its literal
-- for every row it writes; nothing ever reviewed them and nothing ever published them. They
-- have been consumed as if published for months. That script is corrected in the same commit
-- so a freshly seeded estate does not reintroduce this.
--
-- Promoting rather than widening resolution to accept ACTIVE: ACTIVE means something real in
-- this model, and teaching the resolver to serve a review state would make every future
-- genuinely-under-review artifact live the moment it was written.
--
-- Additive. No row is deleted, no content is altered, no version changes.
-- =============================================================================

UPDATE zibo_artifacts
SET status       = 'PUBLISHED',
    published_by = 'V402-migration',
    published_at = COALESCE(published_at, now()),
    updated_at   = now()
WHERE status = 'ACTIVE'
  AND canonical_url IN (
      'https://zibo.mohcc.gov.zw/fhir/CodeSystem/icd10-zw',
      'https://zibo.mohcc.gov.zw/fhir/CodeSystem/edliz-medications',
      'https://zibo.mohcc.gov.zw/fhir/CodeSystem/lab-tests-zw',
      'https://zibo.mohcc.gov.zw/fhir/CodeSystem/provider-specialties-zw',
      'https://zibo.mohcc.gov.zw/fhir/ValueSet/encounter-types-zw',
      'https://zibo.mohcc.gov.zw/fhir/ValueSet/workspace-types-zw'
  );

-- The packs holding them carry the same seeded literal, for the same reason.
UPDATE zibo_packs
SET status       = 'PUBLISHED',
    published_by = 'V402-migration',
    published_at = COALESCE(published_at, now()),
    updated_at   = now()
WHERE status = 'ACTIVE'
  AND pack_id IN (SELECT DISTINCT pa.pack_id
                  FROM zibo_pack_artifacts pa
                  JOIN zibo_artifacts a ON a.artifact_id = pa.artifact_id
                  WHERE a.published_by = 'V402-migration');

-- Guard: no artifact may be left in ACTIVE by this migration without being noticed. If a
-- future seed introduces another review-state row, this fails the migration rather than
-- letting a vocabulary go quietly unresolvable again.
DO $$
DECLARE
    stranded INT;
    urls     TEXT;
BEGIN
    SELECT count(*), string_agg(canonical_url || ' v' || version, ', ')
      INTO stranded, urls
      FROM zibo_artifacts
     WHERE status = 'ACTIVE';

    IF stranded > 0 THEN
        RAISE EXCEPTION
            'V402: % artifact(s) remain in ACTIVE and are therefore unresolvable: %. '
            'Either publish them here or record why they are genuinely under review.',
            stranded, urls;
    END IF;
END $$;
