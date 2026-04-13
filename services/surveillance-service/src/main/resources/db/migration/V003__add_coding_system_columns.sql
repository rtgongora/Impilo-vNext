-- =============================================
-- Service : surveillance-service (Public Health Surveillance)
-- Flyway  : V003__add_coding_system_columns.sql
-- Purpose : Add structured disease coding to surveillance cases
--           per Healthcare Coding Standards doctrine
-- Doctrine: docs/doctrine/healthcare-coding-standards.md
-- =============================================

-- ── surv.cases: add structured disease coding ─────────────────────
-- Surveillance cases need ICD-11 codes for disease classification
-- and SNOMED CT codes for clinical findings.
ALTER TABLE surv.cases
    ADD COLUMN disease_coding_system VARCHAR(255),
    ADD COLUMN disease_code          VARCHAR(100),
    ADD COLUMN disease_display       VARCHAR(500),
    ADD COLUMN snomed_finding_code   VARCHAR(20),
    ADD COLUMN snomed_finding_display VARCHAR(500);

COMMENT ON COLUMN surv.cases.disease_coding_system IS
    'Terminology system URI for disease classification (typically ICD-11)';
COMMENT ON COLUMN surv.cases.disease_code IS
    'ICD-11 disease code for notifiable disease classification';
COMMENT ON COLUMN surv.cases.disease_display IS
    'Display name for the classified disease';
COMMENT ON COLUMN surv.cases.snomed_finding_code IS
    'SNOMED CT clinical finding code for additional detail';

-- ── surv.signal_hits: add coding context ──────────────────────────
ALTER TABLE surv.signal_hits
    ADD COLUMN condition_coding_system VARCHAR(255),
    ADD COLUMN condition_code          VARCHAR(100),
    ADD COLUMN condition_display       VARCHAR(500);

COMMENT ON COLUMN surv.signal_hits.condition_coding_system IS
    'Terminology system URI for the condition that triggered the signal';

-- ── Indexes ───────────────────────────────────────────────────────
CREATE INDEX idx_surv_cases_disease ON surv.cases (disease_code)
    WHERE disease_code IS NOT NULL;
CREATE INDEX idx_surv_signal_hits_condition ON surv.signal_hits (condition_code)
    WHERE condition_code IS NOT NULL;
