-- =====================================================================
-- Service : zibo-service
-- Flyway  : V003__artifact_effective_window.sql
-- Purpose : Add a clinical applicability window (effective_start/end) to
--           zibo_artifacts so governed OBSERVATION_DEFINITION artifacts can
--           express when a reference interval is effective. Additive and
--           nullable for backward compatibility with existing rows.
--
-- Context : Track B (CDS Engine) Phase 0 part 2 — hosts governed FHIR R4
--           ObservationDefinition.qualifiedInterval artifacts (provenance/
--           version/status already present) with effective dates.
-- =====================================================================

ALTER TABLE zibo_artifacts
    ADD COLUMN IF NOT EXISTS effective_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_end   TIMESTAMPTZ;

-- Range lookups by type + applicability window (e.g. ObservationDefinition
-- resolution for a value observed at a given time).
CREATE INDEX IF NOT EXISTS idx_zibo_artifacts_type_effective
    ON zibo_artifacts (fhir_type, effective_start, effective_end);
