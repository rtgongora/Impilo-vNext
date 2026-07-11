-- ============================================================================
-- V019: HPA inspection content catalogue — composable requirement modules,
--       template compositions, classification applicability, sample-template
--       retirement, renewal-reminder operational policy.
--
-- Content source: HPA Registration, Inspection and Renewal Manual Vol 1 (2017)
-- — the CURRENT operative HPA manual (programme owner confirmation 2026-07-11).
-- Module/composition CONTENT is loaded from versioned classpath resources
-- (regulatory/inspection-modules/*.json) by InspectionContentSeeder, which
-- enforces provenance + immutability invariants; this migration owns SCHEMA
-- and status corrections only. Forward-only.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Requirement modules: one versioned module per manual minimum-requirement
--    schedule. COMMON module = "All Types of Health Institutions" (pp16-19).
--    Items are structured JSON with per-item source page/heading provenance.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.inspection_requirement_module (
    module_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(96) NOT NULL,
    version               INT NOT NULL DEFAULT 1,
    title                 VARCHAR(255) NOT NULL,
    scope                 VARCHAR(32) NOT NULL,          -- COMMON | SERVICE_SPECIFIC
    source_doc            VARCHAR(64),
    source_heading        VARCHAR(255),
    source_pages          JSONB,
    notes                 TEXT,
    items                 JSONB NOT NULL,
    item_count            INT NOT NULL DEFAULT 0,
    review_required_count INT NOT NULL DEFAULT 0,
    content_checksum      VARCHAR(64) NOT NULL,          -- immutable-version guard
    status                VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | SOURCE_RECONCILED | VALIDATED_CURRENT_SOURCE | ACTIVE | RETIRED
    approved_by           VARCHAR(255),
    approved_at           TIMESTAMPTZ,
    effective_from        DATE,
    supersedes_module_id  UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- ----------------------------------------------------------------------------
-- 2. Template compositions: ordered module sets (common first). A hospital
--    with pharmacy/lab/theatre/ICU/imaging units receives the common module
--    plus each applicable specific module — never duplicated content.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.inspection_template_composition (
    composition_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(96) NOT NULL,
    version          INT NOT NULL DEFAULT 1,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    module_codes     JSONB NOT NULL,                     -- ordered, e.g. ["MOD-ALL-HEALTH-INSTITUTIONS","MOD-PHARMACY"]
    inspection_types JSONB NOT NULL,                     -- inspection types this composition is valid for
    status           VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
    approved_by      VARCHAR(255),
    approved_at      TIMESTAMPTZ,
    effective_from   DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- ----------------------------------------------------------------------------
-- 3. Applicability: explicit coverage map — which composition applies to which
--    facility classification / tuso facility_type / regulated unit type.
--    No facility may silently receive an irrelevant or generic checklist.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.classification_template_applicability (
    applicability_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_code           VARCHAR(16),
    classification_label VARCHAR(255),                   -- matches tuso.facility_classification.facility_type_label
    facility_type        VARCHAR(64),                    -- matches tuso.facility.facility_type
    unit_type            VARCHAR(64),                    -- matches tuso.facility_unit.unit_type
    composition_code     VARCHAR(96) NOT NULL,
    rationale            TEXT,
    source_doc           VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cta_mapping ON tuso.classification_template_applicability
    (COALESCE(class_code,''), COALESCE(classification_label,''), COALESCE(facility_type,''),
     COALESCE(unit_type,''), composition_code);
CREATE INDEX IF NOT EXISTS idx_cta_label ON tuso.classification_template_applicability(classification_label);
CREATE INDEX IF NOT EXISTS idx_cta_unit ON tuso.classification_template_applicability(unit_type);

-- ----------------------------------------------------------------------------
-- 4. Inspections freeze exact content-version provenance at scheduling time.
-- ----------------------------------------------------------------------------
ALTER TABLE tuso.facility_inspection ADD COLUMN IF NOT EXISTS composition_code VARCHAR(96);
ALTER TABLE tuso.facility_inspection ADD COLUMN IF NOT EXISTS module_versions JSONB;

-- ----------------------------------------------------------------------------
-- 5. Retire the V006 sample templates from operational use. History preserved:
--    rows remain (audit + any historical references), clearly labelled, and
--    are excluded from scheduling by the engine's status guard.
-- ----------------------------------------------------------------------------
UPDATE tuso.inspection_checklist_template
   SET status = 'RETIRED',
       name = name || ' — SAMPLE / NON-REGULATORY / RETIRED',
       description = COALESCE(description, '')
           || ' [RETIRED 2026-07-12: sample skeleton content, never a faithful extraction of the HPA manual;'
           || ' superseded by the manual-derived inspection requirement module catalogue (V019).]'
 WHERE code IN ('GENERAL-MIN-INITIAL-V1', 'CLINIC-INITIAL-V1', 'LAB-INITIAL-V1', 'MATERNITY-ROUTINE-V1')
   AND status <> 'RETIRED';

-- ----------------------------------------------------------------------------
-- 6. Renewal reminder window: activated as PROGRAMME OPERATIONAL POLICY (the
--    HPA manual does not prescribe a reminder window). No effect on
--    certificate validity — expiry remains 31 December per manual §3.1.
-- ----------------------------------------------------------------------------
UPDATE tuso.regulatory_rule
   SET status = 'ACTIVE',
       approved_by = 'PROGRAMME_OWNER',
       approved_at = now(),
       effective_from = CURRENT_DATE,
       source_manual = 'PROGRAMME_OPERATIONAL_POLICY',
       source_pages = NULL,
       statement = 'Days before certificate expiry at which a facility enters RENEWAL_DUE and reminders begin. '
           || 'Operational programme configuration approved by the programme owner — the HPA manual does not '
           || 'prescribe a reminder window. Configurable and versioned; no effect on certificate validity '
           || '(expiry remains 31 December per manual §3.1).'
 WHERE code = 'RENEWAL_DUE_WINDOW_DAYS' AND status = 'PENDING_REGULATOR_APPROVAL';

INSERT INTO tuso.regulatory_rule
    (code, owner, rule_kind, statement, value_json, implementation, source_manual,
     status, approved_by, approved_at, effective_from)
VALUES
    ('RENEWAL_REMINDER_MILESTONE_DAYS', 'TUSO', 'CONFIG',
     'Reminder milestones (days before certificate expiry) at which renewal reminder notifications are emitted. '
         || 'Operational programme configuration — not prescribed by the HPA manual; configurable without code '
         || 'change; no effect on certificate validity.',
     '{"days": [90, 60, 30, 7]}',
     'renewal_reminder_notifications', 'PROGRAMME_OPERATIONAL_POLICY',
     'ACTIVE', 'PROGRAMME_OWNER', now(), CURRENT_DATE)
ON CONFLICT (code, version) DO NOTHING;
