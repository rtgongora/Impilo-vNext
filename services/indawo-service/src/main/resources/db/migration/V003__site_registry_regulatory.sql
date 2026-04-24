-- ============================================================================
-- indawo-service V003 — Public Health Site Registry: licensing + inspection + compliance
-- Canonical regulatory lifecycle for non-facility public health sites (premises).
-- Sibling to (not a duplicate of) TUSO facility-registry.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Extend core sites table with registry + operational fields (backward compatible)
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteEntity`
-- ---------------------------------------------------------------------------
ALTER TABLE ind_sites
    ADD COLUMN IF NOT EXISTS site_code            VARCHAR(64),
    ADD COLUMN IF NOT EXISTS site_category        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS risk_class           VARCHAR(32),
    ADD COLUMN IF NOT EXISTS legal_status         VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ownership_type       VARCHAR(32),
    ADD COLUMN IF NOT EXISTS ownership_name       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS operator_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS registration_pathway VARCHAR(64),
    ADD COLUMN IF NOT EXISTS province             VARCHAR(128),
    ADD COLUMN IF NOT EXISTS district             VARCHAR(128),
    ADD COLUMN IF NOT EXISTS ward                 VARCHAR(128),
    ADD COLUMN IF NOT EXISTS jurisdiction_ref     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS lifecycle_status     VARCHAR(48) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS regulatory_status    VARCHAR(48) NOT NULL DEFAULT 'APPLICATION_IN_PROGRESS',
    ADD COLUMN IF NOT EXISTS operational_status   VARCHAR(48) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS active_flag          BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS metadata             JSONB;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ind_sites_tenant_site_code
    ON ind_sites (tenant_id, site_code)
    WHERE site_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ind_sites_category ON ind_sites (tenant_id, site_category) WHERE site_category IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ind_sites_regulatory_status ON ind_sites (tenant_id, regulatory_status);

-- ---------------------------------------------------------------------------
-- Site operators (owner/operator history)
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteOperatorEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_operators (
    operator_id     UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    site_id         UUID         NOT NULL REFERENCES ind_sites(site_id),
    operator_type   VARCHAR(32)  NOT NULL,
    operator_name   VARCHAR(255) NOT NULL,
    contact_phone   VARCHAR(64),
    contact_email   VARCHAR(255),
    active_flag     BOOLEAN      NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_site_ops_site ON ind_site_operators (tenant_id, site_id);

-- ---------------------------------------------------------------------------
-- Site applications (registration / licensing / renewal / material change)
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteApplicationEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_applications (
    application_id   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    site_id          UUID         REFERENCES ind_sites(site_id),
    application_type VARCHAR(48)  NOT NULL,
    priority         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    fee_state        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    workflow_state   VARCHAR(64)  NOT NULL DEFAULT 'DRAFT',
    review_state     VARCHAR(32),
    final_decision   VARCHAR(32),
    decision_date    TIMESTAMPTZ,
    submission_date  TIMESTAMPTZ,
    applicant_name   VARCHAR(255) NOT NULL,
    applicant_email  VARCHAR(255),
    applicant_phone  VARCHAR(64),
    applicant_org    VARCHAR(255),
    notes            TEXT,
    requested_changes JSONB,
    metadata         JSONB,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_site_apps_site ON ind_site_applications (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_ind_site_apps_state ON ind_site_applications (tenant_id, workflow_state);

-- ---------------------------------------------------------------------------
-- Site documents (app/site artefacts)
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteDocumentEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_documents (
    document_id       UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    site_id           UUID         NOT NULL REFERENCES ind_sites(site_id),
    application_id    UUID         REFERENCES ind_site_applications(application_id),
    document_type     VARCHAR(64)  NOT NULL,
    file_reference    VARCHAR(512) NOT NULL,
    version           INT          NOT NULL DEFAULT 1,
    verification_state VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    uploaded_by       VARCHAR(255),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_by       VARCHAR(255),
    reviewed_at       TIMESTAMPTZ,
    notes             TEXT,
    metadata          JSONB
);
CREATE INDEX IF NOT EXISTS idx_ind_site_docs_site ON ind_site_documents (tenant_id, site_id);

-- ---------------------------------------------------------------------------
-- Licences/permits/certificates
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteLicenceEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_licences (
    licence_id      UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    site_id         UUID         NOT NULL REFERENCES ind_sites(site_id),
    licence_type    VARCHAR(64)  NOT NULL,
    issue_date      DATE         NOT NULL,
    expiry_date     DATE         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    restriction_summary TEXT,
    issued_under_authority VARCHAR(255),
    supersedes_licence_id UUID,
    digital_artifact_ref  VARCHAR(512),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_site_lic_site ON ind_site_licences (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_ind_site_lic_status ON ind_site_licences (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Checklist templates and items
-- Matches `InspectionChecklistTemplateEntity` + `InspectionChecklistItemEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_inspection_checklist_templates (
    template_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    code        VARCHAR(64) NOT NULL,
    version     INT NOT NULL DEFAULT 1,
    name        VARCHAR(255) NOT NULL,
    applicable_site_category VARCHAR(64),
    applicable_inspection_type VARCHAR(64) NOT NULL,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ind_chk_template UNIQUE (tenant_id, code, version)
);

CREATE TABLE IF NOT EXISTS ind_inspection_checklist_items (
    item_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL REFERENCES ind_inspection_checklist_templates(template_id),
    section VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    requirement_text TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'MAJOR',
    evidence_type VARCHAR(32),
    pass_criteria TEXT,
    critical_flag BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    active_flag BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_ind_chk_items_template ON ind_inspection_checklist_items (tenant_id, template_id);

-- ---------------------------------------------------------------------------
-- Inspections
-- Matches `zw.gov.mohcc.impilo.indawo.domain.SiteInspectionEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_inspections (
    inspection_id     UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    site_id           UUID        NOT NULL REFERENCES ind_sites(site_id),
    application_id    UUID        REFERENCES ind_site_applications(application_id),
    inspection_type   VARCHAR(32) NOT NULL,
    template_id       UUID,
    scheduled_date    DATE,
    actual_date       DATE,
    inspector_assignments JSONB,
    status            VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    report_status     VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    outcome           VARCHAR(64),
    recommendation    VARCHAR(64),
    severity_summary  VARCHAR(32),
    next_action       VARCHAR(64),
    next_inspection_due DATE,
    notes             TEXT,
    metadata          JSONB,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_inspections_site ON ind_site_inspections (tenant_id, site_id);

-- ---------------------------------------------------------------------------
-- Findings + compliance actions
-- Matches `SiteInspectionFindingEntity` + `SiteComplianceActionEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_inspection_findings (
    finding_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    inspection_id UUID NOT NULL REFERENCES ind_site_inspections(inspection_id),
    checklist_item_id UUID REFERENCES ind_inspection_checklist_items(item_id),
    status VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'MAJOR',
    comments TEXT,
    evidence_refs JSONB,
    rectification_deadline DATE,
    rectification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_findings_inspection ON ind_site_inspection_findings (tenant_id, inspection_id);

CREATE TABLE IF NOT EXISTS ind_site_compliance_actions (
    action_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID NOT NULL REFERENCES ind_sites(site_id),
    finding_id UUID REFERENCES ind_site_inspection_findings(finding_id),
    action_type VARCHAR(64) NOT NULL,
    owner_ref VARCHAR(255),
    due_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    completion_notes TEXT,
    verified_by VARCHAR(255),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_actions_site ON ind_site_compliance_actions (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_ind_actions_status ON ind_site_compliance_actions (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Enforcement cases (foundation)
-- Matches `SiteEnforcementCaseEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_enforcement_cases (
    case_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID NOT NULL REFERENCES ind_sites(site_id),
    trigger_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    recommendation VARCHAR(128),
    authority_route VARCHAR(128),
    linked_refs JSONB,
    closure_reason TEXT,
    decision_details JSONB,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_ind_cases_site ON ind_site_enforcement_cases (tenant_id, site_id);

-- ---------------------------------------------------------------------------
-- Status history + audit (privileged lifecycle actions)
-- Matches `SiteStatusHistoryEntity` + `AuditEventEntity`
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ind_site_status_history (
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID NOT NULL REFERENCES ind_sites(site_id),
    previous_status VARCHAR(64),
    new_status VARCHAR(64) NOT NULL,
    reason TEXT,
    changed_by VARCHAR(255),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    authority_ctx JSONB
);
CREATE INDEX IF NOT EXISTS idx_ind_site_status_hist ON ind_site_status_history (site_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS ind_audit_events (
    audit_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID REFERENCES ind_sites(site_id),
    actor_ref VARCHAR(255),
    actor_type VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    correlation_id UUID,
    notes TEXT,
    before_json JSONB,
    after_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ind_audit_site ON ind_audit_events (site_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Seed baseline checklist templates/items for a demo tenant
-- (Templates can later be tenant-managed; this provides out-of-box archetypes.)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    demo_tenant UUID := '00000000-0000-0000-0000-000000000001';
    t_food UUID;
    t_school UUID;
    t_market UUID;
    t_hosp UUID;
BEGIN
    INSERT INTO ind_inspection_checklist_templates (tenant_id, code, version, name, applicable_site_category, applicable_inspection_type)
    VALUES
      (demo_tenant, 'FOOD_PREMISES_INITIAL', 1, 'Food premises — initial inspection', 'FOOD_PREMISES', 'INITIAL'),
      (demo_tenant, 'SCHOOL_INITIAL', 1, 'School / ECD — initial inspection', 'SCHOOL_ECD', 'INITIAL'),
      (demo_tenant, 'MARKET_ROUTINE', 1, 'Market / public premises — routine inspection', 'MARKET_PUBLIC', 'ROUTINE'),
      (demo_tenant, 'HOSPITALITY_ROUTINE', 1, 'Hospitality premises — routine inspection', 'HOSPITALITY', 'ROUTINE')
    ON CONFLICT DO NOTHING;

    SELECT template_id INTO t_food FROM ind_inspection_checklist_templates WHERE tenant_id=demo_tenant AND code='FOOD_PREMISES_INITIAL' AND version=1;
    SELECT template_id INTO t_school FROM ind_inspection_checklist_templates WHERE tenant_id=demo_tenant AND code='SCHOOL_INITIAL' AND version=1;
    SELECT template_id INTO t_market FROM ind_inspection_checklist_templates WHERE tenant_id=demo_tenant AND code='MARKET_ROUTINE' AND version=1;
    SELECT template_id INTO t_hosp FROM ind_inspection_checklist_templates WHERE tenant_id=demo_tenant AND code='HOSPITALITY_ROUTINE' AND version=1;

    INSERT INTO ind_inspection_checklist_items (tenant_id, template_id, section, code, requirement_text, severity, critical_flag, display_order)
    VALUES
      (demo_tenant, t_food, 'Hygiene', 'FOOD-HYG-001', 'Handwashing facilities available and functional', 'CRITICAL', TRUE, 10),
      (demo_tenant, t_food, 'Hygiene', 'FOOD-HYG-002', 'Food handlers have protective clothing (aprons, hairnets)', 'MAJOR', FALSE, 20),
      (demo_tenant, t_food, 'Sanitation', 'FOOD-SAN-001', 'Premises have safe water supply and sanitary waste disposal', 'CRITICAL', TRUE, 30)
    ON CONFLICT DO NOTHING;

    INSERT INTO ind_inspection_checklist_items (tenant_id, template_id, section, code, requirement_text, severity, critical_flag, display_order)
    VALUES
      (demo_tenant, t_school, 'WASH', 'SCH-WASH-001', 'Functional latrines/toilets with handwashing stations', 'CRITICAL', TRUE, 10),
      (demo_tenant, t_school, 'Food', 'SCH-FOOD-001', 'School feeding area clean and food stored safely', 'MAJOR', FALSE, 20),
      (demo_tenant, t_school, 'Safety', 'SCH-SAFE-001', 'Play areas free from hazardous materials', 'MAJOR', FALSE, 30)
    ON CONFLICT DO NOTHING;

    INSERT INTO ind_inspection_checklist_items (tenant_id, template_id, section, code, requirement_text, severity, critical_flag, display_order)
    VALUES
      (demo_tenant, t_market, 'Sanitation', 'MKT-SAN-001', 'Waste bins available and waste removal scheduled', 'MAJOR', FALSE, 10),
      (demo_tenant, t_market, 'Water', 'MKT-WAT-001', 'Access to safe water for vendors', 'CRITICAL', TRUE, 20)
    ON CONFLICT DO NOTHING;

    INSERT INTO ind_inspection_checklist_items (tenant_id, template_id, section, code, requirement_text, severity, critical_flag, display_order)
    VALUES
      (demo_tenant, t_hosp, 'Hygiene', 'HOSP-HYG-001', 'Kitchen/food areas hygienic and pest control measures present', 'CRITICAL', TRUE, 10),
      (demo_tenant, t_hosp, 'WASH', 'HOSP-WASH-001', 'Adequate sanitation facilities for guests', 'MAJOR', FALSE, 20)
    ON CONFLICT DO NOTHING;
END $$;

