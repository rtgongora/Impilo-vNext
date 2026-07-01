-- ═══════════════════════════════════════════════════════════════════════
-- Clinical encounter-form metadata on fs_form_schemas.
--
-- forms-service is SoR for form DEFINITIONS + immutable versions. The full WHO-DAK
-- ClinicalFormDefinition JSON continues to live in fs_form_schema_versions.schema_json
-- (immutable = the "preserve exact form version" guarantee). These columns lift the
-- queryable APPLICABILITY + GOVERNANCE metadata onto the schema row so the PCT
-- FormResolver can fetch a candidate catalog slice and the pure FormScopeEngine can
-- bucket forms by patient / cadre / setting.
--
-- Multi-valued applicability is stored as JSON-in-TEXT (parity with schema_json TEXT),
-- parsed by the service; scalar filters are typed columns. Backward compatible: all
-- columns are nullable / defaulted so existing generic forms keep working.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE fs_form_schemas
    ADD COLUMN IF NOT EXISTS form_type               VARCHAR(64),
    ADD COLUMN IF NOT EXISTS care_settings           TEXT,          -- JSON array: OUTPATIENT|INPATIENT|EMERGENCY|TELEMEDICINE|COMMUNITY|OUTREACH|HOME|FIELD
    ADD COLUMN IF NOT EXISTS care_stages             TEXT,          -- JSON array: TRIAGE|INTAKE|ASSESSMENT|CONSULTATION|ADMISSION|WARD_ROUND|REVIEW|DISCHARGE|...
    ADD COLUMN IF NOT EXISTS specialties             TEXT,          -- JSON array: GENERAL|NURSING|MIDWIFERY|SURGERY|PAEDIATRICS|...
    ADD COLUMN IF NOT EXISTS encounter_types         TEXT,          -- JSON array
    ADD COLUMN IF NOT EXISTS encounter_contexts      TEXT,          -- JSON array (EncounterEntity.encounterContext values)
    ADD COLUMN IF NOT EXISTS required_workflow       VARCHAR(48),   -- CadreEngine workflow token this form drives: TRIAGE|ASSESS|DIAGNOSE|PRESCRIBE|CARE_PLAN|...
    ADD COLUMN IF NOT EXISTS obligation_default      VARCHAR(24)    NOT NULL DEFAULT 'OPTIONAL', -- MANDATORY|RECOMMENDED|OPTIONAL
    ADD COLUMN IF NOT EXISTS permitted_cadres        TEXT,          -- JSON array of cadre families / cadres
    ADD COLUMN IF NOT EXISTS min_age_months          INT,
    ADD COLUMN IF NOT EXISTS max_age_months          INT,
    ADD COLUMN IF NOT EXISTS sex_applicability       VARCHAR(16)    DEFAULT 'ALL', -- ALL|MALE|FEMALE
    ADD COLUMN IF NOT EXISTS require_pregnant        BOOLEAN,       -- null=any, true=only pregnant, false=only non-pregnant
    ADD COLUMN IF NOT EXISTS programme_applicability TEXT,          -- JSON array (require at least one)
    ADD COLUMN IF NOT EXISTS facility_levels         TEXT,          -- JSON array
    ADD COLUMN IF NOT EXISTS eligibility_json        TEXT,          -- JSON: arbitrary condition/eligibility rules
    ADD COLUMN IF NOT EXISTS terminology_bindings    TEXT,          -- JSON: field→value-set bindings (Zibo)
    ADD COLUMN IF NOT EXISTS resource_mappings       TEXT,          -- JSON: answer→clinical-resource extraction hints
    ADD COLUMN IF NOT EXISTS indicators              TEXT,          -- JSON: programme indicator mappings
    ADD COLUMN IF NOT EXISTS requires_countersign    BOOLEAN        NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS offline_capable         BOOLEAN        NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS sensitivity             VARCHAR(24)    NOT NULL DEFAULT 'STANDARD', -- STANDARD|SENSITIVE|RESTRICTED
    ADD COLUMN IF NOT EXISTS governance_author       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS governance_reviewer     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS governance_approver     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS effective_date          DATE,
    ADD COLUMN IF NOT EXISTS review_date             DATE,
    ADD COLUMN IF NOT EXISTS source_guideline        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS change_reason           TEXT,
    ADD COLUMN IF NOT EXISTS is_clinical             BOOLEAN        NOT NULL DEFAULT false; -- true = encounter clinical form (resolvable)

-- Catalog query support: resolver fetches ACTIVE/PUBLISHED clinical forms for a tenant.
CREATE INDEX IF NOT EXISTS idx_fs_schemas_clinical_catalog
    ON fs_form_schemas (tenant_id, is_clinical, status);

COMMENT ON COLUMN fs_form_schemas.required_workflow IS
    'CadreEngine workflow token the form drives; PCT FormScopeEngine intersects it with the cadre permitted-workflow set (GAP-4 unification).';
COMMENT ON COLUMN fs_form_schemas.is_clinical IS
    'When true the form is an encounter clinical form eligible for the PCT resolver catalog.';
