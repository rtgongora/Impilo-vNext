-- ============================================================================
-- V054 — WorkMode governed catalog + role_template → allowed_modes grants.
--
-- Mode (Clinical Care / Facility Management / Technical Support / ...) has
-- had no representation anywhere: `work_mode` existed only as a cosmetic
-- notification query param in experience-bff, never persisted, never
-- authorized on. This adds the governed grant table beside
-- role_template_catalog (V043): which role_template_id (Vashandi duty
-- template, org-registry appointment role, or TUSO facility-admin role — the
-- SAME heterogeneous key space role_template_catalog already spans) may hold
-- which WorkMode, and which mode is its default.
--
-- work_mode_catalog itself mirrors the WorkMode Java enum's constructor
-- fields (anchor_kind, clinical_data_access, default_purpose_of_use) so a
-- caller (identity-service, the resolver) can look up mode semantics without
-- a compile-time dependency, and so a golden-thread test can assert the two
-- never drift.
-- ============================================================================

-- ── Part A: the mode vocabulary itself (mirrors WorkMode.java) ──────────────
CREATE TABLE IF NOT EXISTS tshepo_authz.work_mode_catalog (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    mode_code               VARCHAR(64) NOT NULL,
    display_label           VARCHAR(255) NOT NULL,
    anchor_kind             VARCHAR(32) NOT NULL,
        -- FACILITY | ORGANISATION | JURISDICTION | PROGRAMME | FACILITY_OR_ORGANISATION
    clinical_data_access    VARCHAR(16) NOT NULL,
        -- IDENTIFIED | AGGREGATE | NONE
    default_purpose_of_use  VARCHAR(32) NOT NULL,
    requires_provider       BOOLEAN NOT NULL DEFAULT false,
    active                  BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_work_mode_catalog UNIQUE (tenant_id, mode_code),
    CONSTRAINT chk_work_mode_anchor_kind
        CHECK (anchor_kind IN ('FACILITY','ORGANISATION','JURISDICTION','PROGRAMME','FACILITY_OR_ORGANISATION')),
    CONSTRAINT chk_work_mode_clinical_access
        CHECK (clinical_data_access IN ('IDENTIFIED','AGGREGATE','NONE'))
);

INSERT INTO tshepo_authz.work_mode_catalog
    (tenant_id, mode_code, display_label, anchor_kind, clinical_data_access, default_purpose_of_use, requires_provider)
VALUES
('00000000-0000-0000-0000-000000000001'::uuid, 'CLINICAL_CARE',            'Clinical Care',            'FACILITY',                 'IDENTIFIED', 'TREATMENT',       true),
('00000000-0000-0000-0000-000000000001'::uuid, 'VIRTUAL_CARE',             'Virtual Care',             'FACILITY_OR_ORGANISATION', 'IDENTIFIED', 'TREATMENT',       true),
('00000000-0000-0000-0000-000000000001'::uuid, 'DEPARTMENT_MANAGEMENT',    'Department Management',   'FACILITY',                 'AGGREGATE',  'OPERATIONS',      false),
('00000000-0000-0000-0000-000000000001'::uuid, 'FACILITY_MANAGEMENT',      'Facility Management',     'FACILITY',                 'AGGREGATE',  'OPERATIONS',      false),
('00000000-0000-0000-0000-000000000001'::uuid, 'JURISDICTION_OPERATIONS',  'Jurisdiction Operations',  'JURISDICTION',             'AGGREGATE',  'PUBLIC_HEALTH',   false),
('00000000-0000-0000-0000-000000000001'::uuid, 'PROGRAMME_MANAGEMENT',     'Programme Management',    'PROGRAMME',                'AGGREGATE',  'PUBLIC_HEALTH',   false),
('00000000-0000-0000-0000-000000000001'::uuid, 'TECHNICAL_SUPPORT',        'Technical Support',       'ORGANISATION',             'NONE',       'SYSTEM',          false),
('00000000-0000-0000-0000-000000000001'::uuid, 'FACILITY_SUPPORT',         'Facility Support',        'FACILITY',                 'NONE',       'OPERATIONS',      false),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATORY_OPERATIONS',    'Regulatory Operations',   'ORGANISATION',             'NONE',       'REGULATORY_DUTY', false),
('00000000-0000-0000-0000-000000000001'::uuid, 'INSPECTION_COMPLIANCE',    'Inspection & Compliance', 'ORGANISATION',             'NONE',       'REGULATORY_DUTY', false)
ON CONFLICT ON CONSTRAINT uq_work_mode_catalog DO NOTHING;

-- ── Part B: role_template → allowed modes, with one default per template ────
CREATE TABLE IF NOT EXISTS tshepo_authz.role_template_mode (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    role_template_id VARCHAR(128) NOT NULL,
    mode_code        VARCHAR(64) NOT NULL,
    default_mode     BOOLEAN NOT NULL DEFAULT false,
    rank             SMALLINT NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_template_mode UNIQUE (tenant_id, role_template_id, mode_code)
);

CREATE INDEX IF NOT EXISTS ix_role_template_mode_lookup
    ON tshepo_authz.role_template_mode (tenant_id, role_template_id) WHERE active;

-- A partial unique index would be the cleanest way to enforce "at most one
-- default per (tenant, role_template_id)", but Postgres partial-unique
-- indexes don't compose with ON CONFLICT upserts across separate rows here,
-- so this is enforced at the write path (WorkModeCatalog callers / the
-- admin API in a later wave), not the schema. Seeds below are hand-checked.
CREATE INDEX IF NOT EXISTS ix_role_template_mode_default
    ON tshepo_authz.role_template_mode (tenant_id, role_template_id) WHERE active AND default_mode;

INSERT INTO tshepo_authz.role_template_mode
    (tenant_id, role_template_id, mode_code, default_mode, rank)
VALUES
-- Vashandi clinical duty templates (V043) → CLINICAL_CARE.
('00000000-0000-0000-0000-000000000001'::uuid, 'NURSE_GENERAL',       'CLINICAL_CARE', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'NURSE_ONCOLOGY_SNR',  'CLINICAL_CARE', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'MEDICAL_OFFICER',     'CLINICAL_CARE', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'PHARMACIST_DISPENSE', 'CLINICAL_CARE', true, 0),
-- WARD_CHARGE_NURSE (V051) is dual-mode: clinical duty by default, may switch
-- to managing their own ward/department.
('00000000-0000-0000-0000-000000000001'::uuid, 'WARD_CHARGE_NURSE',   'CLINICAL_CARE',         true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'WARD_CHARGE_NURSE',   'DEPARTMENT_MANAGEMENT', false, 1),

-- org-registry regulatory appointment roles (org_registry_appointment_role
-- closed vocabulary) → REGULATORY_OPERATIONS by default.
('00000000-0000-0000-0000-000000000001'::uuid, 'REGISTRAR',                'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'DEPUTY_REGISTRAR',         'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGISTRATION_OFFICER',     'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'CPD_OFFICER',              'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'INVESTIGATIONS_OFFICER',   'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'COMMITTEE_MEMBER',         'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'FINANCE_OFFICER',          'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'RECORDS_OFFICER',          'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'LEGAL_OFFICER',            'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'COUNCIL_CEO',              'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'HPA_OVERSIGHT_OFFICER',    'REGULATORY_OPERATIONS', true, 0),
-- INSPECTOR and HPA_INSPECTORATE_OFFICER: inspection is their default mode,
-- with general regulatory operations also available.
('00000000-0000-0000-0000-000000000001'::uuid, 'INSPECTOR',                'INSPECTION_COMPLIANCE', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'INSPECTOR',                'REGULATORY_OPERATIONS', false, 1),
('00000000-0000-0000-0000-000000000001'::uuid, 'HPA_INSPECTORATE_OFFICER', 'INSPECTION_COMPLIANCE', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'HPA_INSPECTORATE_OFFICER', 'REGULATORY_OPERATIONS', false, 1),

-- Regulator administration roles (V052) → REGULATORY_OPERATIONS, except
-- NATIONAL_TRUST_ADMINISTRATOR which is platform-trust administration, not
-- a statutory regulatory duty — closest real fit is TECHNICAL_SUPPORT
-- (no clinical access, organisation-scoped platform administration).
('00000000-0000-0000-0000-000000000001'::uuid, 'FOUNDING_REGULATOR_ADMINISTRATOR', 'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_BUSINESS_OWNER',         'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_SECURITY_ADMINISTRATOR', 'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_WORKSPACE_ADMINISTRATOR','REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_RECORDS_ADMINISTRATOR',  'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_PROCESS_OWNER',          'REGULATORY_OPERATIONS', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'NATIONAL_TRUST_ADMINISTRATOR',     'TECHNICAL_SUPPORT',     true, 0),

-- TUSO facility_admin_appointment closed vocabulary.
('00000000-0000-0000-0000-000000000001'::uuid, 'FACILITY_ADMINISTRATOR', 'FACILITY_MANAGEMENT', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'SERVICE_CONFIG_MANAGER', 'FACILITY_MANAGEMENT', true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'FACILITY_VIEWER',        'FACILITY_SUPPORT',     true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'DATA_STEWARD',           'FACILITY_SUPPORT',     true, 0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATORY_LIAISON',     'FACILITY_MANAGEMENT',   true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATORY_LIAISON',     'REGULATORY_OPERATIONS', false, 1),

-- support-service scoped support roles (A3) → TECHNICAL_SUPPORT by default;
-- FACILITY_SUPPORT is also available for a facility-scoped support team
-- (the resolver picks based on the team's actual scope, not the role alone).
('00000000-0000-0000-0000-000000000001'::uuid, 'SUPPORT_OFFICER',        'TECHNICAL_SUPPORT', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'SUPPORT_OFFICER',        'FACILITY_SUPPORT',   false, 1),
('00000000-0000-0000-0000-000000000001'::uuid, 'SENIOR_SUPPORT_OFFICER', 'TECHNICAL_SUPPORT', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'SENIOR_SUPPORT_OFFICER', 'FACILITY_SUPPORT',   false, 1),
('00000000-0000-0000-0000-000000000001'::uuid, 'TEAM_LEAD',              'TECHNICAL_SUPPORT', true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'TEAM_LEAD',              'FACILITY_SUPPORT',   false, 1),
('00000000-0000-0000-0000-000000000001'::uuid, 'BIOMEDICAL_ENGINEER',    'FACILITY_SUPPORT',   true,  0),
('00000000-0000-0000-0000-000000000001'::uuid, 'BIOMEDICAL_ENGINEER',    'TECHNICAL_SUPPORT', false, 1)
ON CONFLICT ON CONSTRAINT uq_role_template_mode DO NOTHING;

COMMENT ON TABLE tshepo_authz.work_mode_catalog IS
    'Governed WorkMode vocabulary, mirroring libs/tshepo-contracts WorkMode.java. '
    'A golden-thread test asserts these never drift apart.';
COMMENT ON TABLE tshepo_authz.role_template_mode IS
    'Which WorkMode(s) a role_template_id (Vashandi duty template | org-registry '
    'appointment role | TUSO facility-admin role) may hold, and which is default. '
    'Open-ended WGV role_definitions not seeded here fall back to deterministic '
    'derivation from (role_category, role_level, target_type) at the resolver — '
    'see the Work Context Resolution programme plan, Phase C.';
