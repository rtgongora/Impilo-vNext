-- =============================================
-- Service : coverage-service (Coverage & Claims Engine)
-- Flyway  : V004__add_coding_system_columns.sql
-- Purpose : Add structured diagnosis and procedure coding
--           to claims and preauth tables per Healthcare
--           Coding Standards doctrine
-- Doctrine: docs/doctrine/healthcare-coding-standards.md
-- =============================================

-- ── cv_claims: add structured diagnosis and procedure coding ──────
ALTER TABLE cv_claims
    ADD COLUMN primary_diagnosis_system  VARCHAR(255),
    ADD COLUMN primary_diagnosis_code    VARCHAR(100),
    ADD COLUMN primary_diagnosis_display VARCHAR(500),
    ADD COLUMN procedure_codings         JSONB;

COMMENT ON COLUMN cv_claims.primary_diagnosis_system IS
    'ICD-11 system URI for the primary diagnosis on this claim';
COMMENT ON COLUMN cv_claims.primary_diagnosis_code IS
    'ICD-11 diagnosis code';
COMMENT ON COLUMN cv_claims.primary_diagnosis_display IS
    'Display name for the primary diagnosis';
COMMENT ON COLUMN cv_claims.procedure_codings IS
    'Array of CodeableConcept objects [{system, code, display}] for procedures (CPT/SNOMED CT)';

-- ── cv_preauth_requests: add structured coding ────────────────────
ALTER TABLE cv_preauth_requests
    ADD COLUMN diagnosis_system  VARCHAR(255),
    ADD COLUMN diagnosis_code    VARCHAR(100),
    ADD COLUMN diagnosis_display VARCHAR(500),
    ADD COLUMN procedure_system  VARCHAR(255),
    ADD COLUMN procedure_code    VARCHAR(100),
    ADD COLUMN procedure_display VARCHAR(500);

COMMENT ON COLUMN cv_preauth_requests.diagnosis_system IS
    'ICD-11 system URI for the diagnosis requiring preauthorization';
COMMENT ON COLUMN cv_preauth_requests.procedure_system IS
    'CPT or SNOMED CT system URI for the procedure requiring preauthorization';

-- ── Indexes ───────────────────────────────────────────────────────
CREATE INDEX idx_cv_claims_diagnosis ON cv_claims (primary_diagnosis_code)
    WHERE primary_diagnosis_code IS NOT NULL;
CREATE INDEX idx_cv_preauth_diagnosis ON cv_preauth_requests (diagnosis_code)
    WHERE diagnosis_code IS NOT NULL;
