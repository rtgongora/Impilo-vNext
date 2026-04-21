-- =============================================
-- Service : tuso-service
-- Schema  : tuso
-- Flyway  : V006__facility_regulatory_operations.sql
-- Purpose : Facility Regulatory Operations Platform
-- =============================================

ALTER TABLE tuso.facility
    ADD COLUMN IF NOT EXISTS alias_names JSONB,
    ADD COLUMN IF NOT EXISTS operating_entity VARCHAR(255),
    ADD COLUMN IF NOT EXISTS facility_class VARCHAR(100),
    ADD COLUMN IF NOT EXISTS facility_category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS legal_status VARCHAR(100),
    ADD COLUMN IF NOT EXISTS registration_pathway VARCHAR(64),
    ADD COLUMN IF NOT EXISTS institution_file_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS regulatory_status VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS regulatory_status_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS metadata JSONB;

CREATE INDEX IF NOT EXISTS idx_facility_regulatory_status ON tuso.facility (regulatory_status);
CREATE INDEX IF NOT EXISTS idx_facility_registration_pathway ON tuso.facility (registration_pathway);

CREATE TABLE IF NOT EXISTS tuso.facility_unit (
    id                      BIGSERIAL PRIMARY KEY,
    facility_id             BIGINT NOT NULL REFERENCES tuso.facility(id),
    unit_type               VARCHAR(64) NOT NULL,
    service_line            VARCHAR(100),
    name                    VARCHAR(255) NOT NULL,
    registration_required   BOOLEAN NOT NULL DEFAULT false,
    regulatory_status       VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    certificate_status      VARCHAR(64),
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_facility_unit_facility ON tuso.facility_unit (facility_id, regulatory_status);

CREATE TABLE IF NOT EXISTS tuso.practitioner_in_charge_assignment (
    id                        BIGSERIAL PRIMARY KEY,
    facility_id               BIGINT NOT NULL REFERENCES tuso.facility(id),
    provider_public_id        VARCHAR(255) NOT NULL,
    role                      VARCHAR(64) NOT NULL DEFAULT 'PRACTITIONER_IN_CHARGE',
    start_date                DATE NOT NULL,
    end_date                  DATE,
    approval_state            VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    source_council_reference  VARCHAR(255),
    notes                     TEXT,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_pic_assignment_facility ON tuso.practitioner_in_charge_assignment (facility_id, approval_state);

CREATE TABLE IF NOT EXISTS tuso.facility_application (
    application_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    facility_id              BIGINT REFERENCES tuso.facility(id),
    application_type         VARCHAR(64) NOT NULL,
    pathway                  VARCHAR(64),
    applicant_name           VARCHAR(255) NOT NULL,
    applicant_email          VARCHAR(255),
    applicant_phone          VARCHAR(64),
    applicant_organisation   VARCHAR(255),
    submission_date          TIMESTAMPTZ,
    current_workflow_state   VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    priority                 VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    fee_state                VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    committee_state          VARCHAR(64) NOT NULL DEFAULT 'NOT_STARTED',
    final_decision           VARCHAR(64),
    decision_date            TIMESTAMPTZ,
    institution_file_number  VARCHAR(64),
    requested_changes        JSONB,
    metadata                 JSONB,
    workflow_notes           TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_facility_application_facility ON tuso.facility_application (facility_id, current_workflow_state);
CREATE INDEX IF NOT EXISTS idx_facility_application_tenant ON tuso.facility_application (tenant_id, application_type, current_workflow_state);

CREATE TABLE IF NOT EXISTS tuso.inspection_checklist_template (
    template_id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                            VARCHAR(128) NOT NULL UNIQUE,
    name                            VARCHAR(255) NOT NULL,
    version                         INT NOT NULL DEFAULT 1,
    facility_type_applicability     VARCHAR(100),
    facility_category_applicability VARCHAR(100),
    inspection_type_applicability   VARCHAR(64) NOT NULL,
    active                          BOOLEAN NOT NULL DEFAULT true,
    description                     TEXT,
    items_json                      JSONB NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_checklist_template_active ON tuso.inspection_checklist_template (active, inspection_type_applicability);

CREATE TABLE IF NOT EXISTS tuso.facility_inspection (
    inspection_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID NOT NULL,
    facility_id                BIGINT NOT NULL REFERENCES tuso.facility(id),
    application_id             UUID REFERENCES tuso.facility_application(application_id),
    inspection_type            VARCHAR(64) NOT NULL,
    template_id                UUID REFERENCES tuso.inspection_checklist_template(template_id),
    scheduled_date             DATE,
    actual_date                DATE,
    inspector_assignments      JSONB,
    status                     VARCHAR(64) NOT NULL DEFAULT 'SCHEDULED',
    report_status              VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    outcome                    VARCHAR(64),
    recommendation             VARCHAR(128),
    severity_summary           VARCHAR(64),
    next_action                VARCHAR(128),
    next_inspection_due_date   DATE,
    capture_mode               VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    offline_capture_reference  VARCHAR(128),
    evidence_summary           JSONB,
    notes                      TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_facility_inspection_facility ON tuso.facility_inspection (facility_id, inspection_type, status);
CREATE INDEX IF NOT EXISTS idx_facility_inspection_application ON tuso.facility_inspection (application_id, status);

CREATE TABLE IF NOT EXISTS tuso.facility_document (
    document_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    facility_id           BIGINT REFERENCES tuso.facility(id),
    application_id        UUID REFERENCES tuso.facility_application(application_id),
    document_type         VARCHAR(64) NOT NULL,
    file_reference        VARCHAR(512) NOT NULL,
    version               INT NOT NULL DEFAULT 1,
    verification_state    VARCHAR(64) NOT NULL DEFAULT 'UNVERIFIED',
    uploaded_by           VARCHAR(255) NOT NULL,
    uploaded_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata              JSONB,
    notes                 TEXT
);

CREATE INDEX IF NOT EXISTS idx_facility_document_facility ON tuso.facility_document (facility_id, document_type);
CREATE INDEX IF NOT EXISTS idx_facility_document_application ON tuso.facility_document (application_id, document_type);

CREATE TABLE IF NOT EXISTS tuso.inspection_finding (
    finding_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_id             UUID NOT NULL REFERENCES tuso.facility_inspection(inspection_id),
    checklist_item_code       VARCHAR(64) NOT NULL,
    checklist_section         VARCHAR(128),
    requirement_text          TEXT NOT NULL,
    critical_flag             BOOLEAN NOT NULL DEFAULT false,
    status                    VARCHAR(32) NOT NULL,
    severity                  VARCHAR(32) NOT NULL,
    comments                  TEXT,
    evidence_refs             JSONB,
    rectification_deadline    DATE,
    rectification_status      VARCHAR(64) NOT NULL DEFAULT 'NOT_REQUIRED',
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_inspection_finding_inspection ON tuso.inspection_finding (inspection_id, status, rectification_status);

CREATE TABLE IF NOT EXISTS tuso.compliance_action (
    action_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID NOT NULL,
    facility_id                BIGINT NOT NULL REFERENCES tuso.facility(id),
    inspection_finding_id      UUID NOT NULL REFERENCES tuso.inspection_finding(finding_id),
    action_type                VARCHAR(64) NOT NULL,
    owner_name                 VARCHAR(255),
    owner_role                 VARCHAR(128),
    due_date                   DATE NOT NULL,
    status                     VARCHAR(64) NOT NULL DEFAULT 'OPEN',
    completion_notes           TEXT,
    completion_evidence_refs   JSONB,
    verified_by                VARCHAR(255),
    verified_at                TIMESTAMPTZ,
    verification_notes         TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_compliance_action_facility ON tuso.compliance_action (facility_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_compliance_action_finding ON tuso.compliance_action (inspection_finding_id, status);

CREATE TABLE IF NOT EXISTS tuso.committee_review (
    review_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    facility_id            BIGINT NOT NULL REFERENCES tuso.facility(id),
    application_id         UUID REFERENCES tuso.facility_application(application_id),
    inspection_id          UUID REFERENCES tuso.facility_inspection(inspection_id),
    committee_type         VARCHAR(64) NOT NULL,
    scheduled_date         DATE,
    decision               VARCHAR(64) NOT NULL,
    notes                  TEXT,
    resolution_reference   VARCHAR(128),
    authority_context      VARCHAR(128),
    decided_by             VARCHAR(255) NOT NULL,
    decided_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata               JSONB
);

CREATE INDEX IF NOT EXISTS idx_committee_review_application ON tuso.committee_review (application_id, decision);

CREATE TABLE IF NOT EXISTS tuso.facility_certificate (
    certificate_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    facility_id                 BIGINT NOT NULL REFERENCES tuso.facility(id),
    application_id              UUID REFERENCES tuso.facility_application(application_id),
    certificate_type            VARCHAR(64) NOT NULL,
    certificate_number          VARCHAR(128) NOT NULL,
    issue_date                  DATE NOT NULL,
    expiry_date                 DATE,
    status                      VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    issued_under_authority      VARCHAR(128) NOT NULL,
    digital_artifact_reference  VARCHAR(512),
    supersedes_certificate_id   UUID REFERENCES tuso.facility_certificate(certificate_id),
    metadata                    JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_by                   VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_facility_certificate_facility ON tuso.facility_certificate (facility_id, status, expiry_date);
CREATE UNIQUE INDEX IF NOT EXISTS idx_facility_certificate_number ON tuso.facility_certificate (certificate_number);

CREATE TABLE IF NOT EXISTS tuso.enforcement_case (
    case_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    facility_id             BIGINT NOT NULL REFERENCES tuso.facility(id),
    application_id          UUID REFERENCES tuso.facility_application(application_id),
    trigger_type            VARCHAR(64) NOT NULL,
    status                  VARCHAR(64) NOT NULL DEFAULT 'OPEN',
    recommendation          VARCHAR(128),
    authority_route         VARCHAR(128),
    linked_inspection_ids   JSONB,
    linked_evidence         JSONB,
    closure_reason          TEXT,
    decision_details        TEXT,
    opened_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at               TIMESTAMPTZ,
    opened_by               VARCHAR(255) NOT NULL,
    updated_by              VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_enforcement_case_facility ON tuso.enforcement_case (facility_id, status);

CREATE TABLE IF NOT EXISTS tuso.facility_status_history (
    id                    BIGSERIAL PRIMARY KEY,
    facility_id           BIGINT NOT NULL REFERENCES tuso.facility(id),
    regulatory_status     VARCHAR(64) NOT NULL,
    application_state     VARCHAR(64),
    changed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by            VARCHAR(255) NOT NULL,
    authority_context     VARCHAR(128),
    reason                TEXT,
    source_application_id UUID
);

CREATE INDEX IF NOT EXISTS idx_facility_status_history_facility ON tuso.facility_status_history (facility_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS tuso.facility_audit_event (
    audit_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    facility_id          BIGINT REFERENCES tuso.facility(id),
    application_id       UUID REFERENCES tuso.facility_application(application_id),
    actor_id             VARCHAR(255) NOT NULL,
    actor_type           VARCHAR(128),
    authority_context    VARCHAR(128),
    action               VARCHAR(128) NOT NULL,
    target_entity_type   VARCHAR(64) NOT NULL,
    target_entity_id     VARCHAR(255) NOT NULL,
    before_state         JSONB,
    after_state          JSONB,
    correlation_id       VARCHAR(64),
    metadata             JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_facility_audit_event_facility ON tuso.facility_audit_event (facility_id, created_at DESC);

INSERT INTO tuso.inspection_checklist_template (
    template_id, code, name, version, facility_type_applicability, facility_category_applicability,
    inspection_type_applicability, active, description, items_json
) VALUES
    (
        '7c84ee7f-bd5e-4cc4-9c37-5aaf973fd001',
        'GENERAL-MIN-INITIAL-V1',
        'General Minimum Requirements - Initial Registration',
        1,
        NULL,
        NULL,
        'INITIAL',
        true,
        'Baseline institutional requirements applicable to all health institutions.',
        '[
          {"code":"GEN-01","section":"Governance","requirement":"Evidence of lawful ownership or operating authority is available.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"Valid incorporation, mandate, or authority file.","critical":true,"order":1},
          {"code":"GEN-02","section":"Administration","requirement":"An HPA institution file and core application dossier are complete.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"Application, plans, and applicant details are filed.","critical":true,"order":2},
          {"code":"GEN-03","section":"Safety","requirement":"Premises have basic fire, waste, and infection prevention controls.","severity":"CRITICAL","evidenceType":"PHOTO","passCriteria":"Safety controls visibly implemented.","critical":true,"order":3},
          {"code":"GEN-04","section":"Staffing","requirement":"A practitioner in charge or accountable lead is identified.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"Named accountable professional with supporting registration details.","critical":true,"order":4},
          {"code":"GEN-05","section":"Records","requirement":"Registers for services, incidents, and inspections are maintained.","severity":"MODERATE","evidenceType":"DOCUMENT","passCriteria":"Register templates or logs are present.","critical":false,"order":5}
        ]'::jsonb
    ),
    (
        '7c84ee7f-bd5e-4cc4-9c37-5aaf973fd002',
        'CLINIC-INITIAL-V1',
        'Clinic / Consulting Room Checklist',
        1,
        'CLINIC',
        NULL,
        'INITIAL',
        true,
        'Sample checklist for clinics and consulting rooms.',
        '[
          {"code":"CLC-01","section":"Clinical Space","requirement":"Consultation room layout preserves privacy and confidentiality.","severity":"MAJOR","evidenceType":"PHOTO","passCriteria":"Private consultation room or screened area available.","critical":true,"order":1},
          {"code":"CLC-02","section":"Equipment","requirement":"Basic diagnostic set and emergency medicines are present.","severity":"CRITICAL","evidenceType":"PHOTO","passCriteria":"Essential equipment and emergency tray available.","critical":true,"order":2},
          {"code":"CLC-03","section":"Utilities","requirement":"Reliable power and water arrangements exist.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"Utilities or backup arrangements documented.","critical":false,"order":3}
        ]'::jsonb
    ),
    (
        '7c84ee7f-bd5e-4cc4-9c37-5aaf973fd003',
        'LAB-INITIAL-V1',
        'Laboratory Checklist',
        1,
        'LABORATORY',
        NULL,
        'INITIAL',
        true,
        'Sample checklist for laboratories.',
        '[
          {"code":"LAB-01","section":"Biosafety","requirement":"Sample handling, PPE, and waste segregation controls are implemented.","severity":"CRITICAL","evidenceType":"PHOTO","passCriteria":"Observed biosafety controls meet baseline standard.","critical":true,"order":1},
          {"code":"LAB-02","section":"Quality","requirement":"Quality control records and SOPs are available.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"QC records and SOPs are current.","critical":true,"order":2},
          {"code":"LAB-03","section":"Equipment","requirement":"Core analyser or bench equipment maintenance logs are current.","severity":"MODERATE","evidenceType":"DOCUMENT","passCriteria":"Maintenance or calibration records available.","critical":false,"order":3}
        ]'::jsonb
    ),
    (
        '7c84ee7f-bd5e-4cc4-9c37-5aaf973fd004',
        'MATERNITY-ROUTINE-V1',
        'Maternity / Imaging Routine Oversight Checklist',
        1,
        'MATERNITY',
        NULL,
        'ROUTINE',
        true,
        'Sample oversight checklist for maternity and higher-risk service lines.',
        '[
          {"code":"MAT-01","section":"Emergency Readiness","requirement":"Emergency transfer and escalation protocols are displayed and understood.","severity":"CRITICAL","evidenceType":"PHOTO","passCriteria":"Escalation protocol and contact chain visible.","critical":true,"order":1},
          {"code":"MAT-02","section":"Medicines","requirement":"Critical maternal emergency medicines are stocked and within date.","severity":"CRITICAL","evidenceType":"PHOTO","passCriteria":"Tracer medicines are stocked and valid.","critical":true,"order":2},
          {"code":"MAT-03","section":"Records","requirement":"Labour or procedure records are complete and retrievable.","severity":"MAJOR","evidenceType":"DOCUMENT","passCriteria":"Recent records reviewed are complete.","critical":false,"order":3}
        ]'::jsonb
    )
ON CONFLICT (code) DO NOTHING;
