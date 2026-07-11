-- ============================================================================
-- V018: HPA regulatory platform — premises, catalogues, rules, PIC nomination,
--       inspection visits/responses, RFI, external council review, template
--       governance, certificate verification.
--
-- Source: HPA Registration, Inspection and Renewal Manual Vol 1 (2017) via the
-- curated pack (source_manual = 'HPA-2017-V1'). The manual is the CURRENT
-- operative HPA manual — confirmed IN FORCE by the programme owner on
-- 2026-07-11, effective until formally superseded. Faithful, direct
-- representations of its content are seeded ACTIVE/VALIDATED with source-page
-- provenance; only items whose digital form adds interpretation, depends on a
-- separate statutory instrument (e.g. fee amounts per SI 78 of 2017), or whose
-- extraction is not yet faithful remain PENDING_REGULATOR_APPROVAL. Rules stay
-- versioned and configurable so future amendments apply without code change.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Premises: a physical site is a first-class identity, separate from the
--    regulated facility. Shared premises = multiple active occupancies.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.facility_premises (
    premises_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    label             VARCHAR(255) NOT NULL,
    address_line1     VARCHAR(255),
    address_line2     VARCHAR(255),
    city              VARCHAR(100),
    province          VARCHAR(100),
    district          VARCHAR(100),
    ward              VARCHAR(100),
    postal_code       VARCHAR(32),
    latitude          NUMERIC(10,7),
    longitude         NUMERIC(10,7),
    occupancy_tenure  VARCHAR(64),           -- OWNED | LEASED | SHARED | OTHER (policy vocabulary)
    tenure_evidence_ref VARCHAR(512),
    local_authority_report_ref VARCHAR(512), -- local-authority health report reference (manual pp.6-7)
    status            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RETIRED
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_premises_tenant ON tuso.facility_premises(tenant_id);
CREATE INDEX IF NOT EXISTS idx_premises_status ON tuso.facility_premises(status);

-- Occupancy link: which regulated facility occupies which premises, when.
-- >1 ACTIVE occupancy on one premises IS the shared-premises arrangement
-- (manual pp.8-9: one address may host separately registered institutions).
CREATE TABLE IF NOT EXISTS tuso.facility_premises_occupancy (
    occupancy_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    premises_id       UUID NOT NULL REFERENCES tuso.facility_premises(premises_id),
    facility_id       BIGINT NOT NULL REFERENCES tuso.facility(id),
    occupancy_role    VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',   -- PRIMARY | SHARED_OCCUPANT
    effective_from    DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to      DATE,
    relocation_application_id UUID,          -- application that created/ended this occupancy
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_occupancy_premises ON tuso.facility_premises_occupancy(premises_id);
CREATE INDEX IF NOT EXISTS idx_occupancy_facility ON tuso.facility_premises_occupancy(facility_id);

-- ----------------------------------------------------------------------------
-- 2. Data-driven catalogues (faithful transcriptions of HPA-2017-V1 schedules).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.regulatory_application_type (
    code                  VARCHAR(64) PRIMARY KEY,
    display_name          VARCHAR(255) NOT NULL,
    engine_type           VARCHAR(64) NOT NULL,     -- maps to FacilityApplicationType enum key
    route                 VARCHAR(64),              -- PRIVATE | PUBLIC_MISSION_LA | ANY
    council_review_required BOOLEAN NOT NULL DEFAULT false,
    inspection_required   BOOLEAN NOT NULL DEFAULT true,
    fee_required          BOOLEAN NOT NULL DEFAULT true,
    evidence_requirements JSONB,                    -- [{code,label,mandatory}]
    manual_route_summary  TEXT,
    source_manual         VARCHAR(64),
    source_page_start     INT,
    source_page_end       INT,
    validation_status     VARCHAR(64) NOT NULL DEFAULT 'REFERENCE_REQUIRES_REGULATOR_VALIDATION',
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tuso.facility_classification (
    classification_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_code         VARCHAR(16) NOT NULL,        -- A | B | C
    facility_type_label VARCHAR(255) NOT NULL,
    source_manual      VARCHAR(64),
    source_page        INT,
    validation_status  VARCHAR(64) NOT NULL DEFAULT 'REFERENCE_REQUIRES_REGULATOR_VALIDATION',
    active             BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (class_code, facility_type_label)
);

CREATE TABLE IF NOT EXISTS tuso.inspection_type_reference (
    code           VARCHAR(64) PRIMARY KEY,          -- matches FacilityInspectionType enum
    display_name   VARCHAR(255) NOT NULL,
    purpose        TEXT,
    source_manual  VARCHAR(64),
    source_page    INT,
    validation_status VARCHAR(64) NOT NULL DEFAULT 'REFERENCE_REQUIRES_REGULATOR_VALIDATION',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- 3. Configurable, versioned regulatory rules. The engine consults ACTIVE
--    rules first; PENDING_REGULATOR_APPROVAL rows are visible-but-advisory and
--    the engine falls back to conservative code defaults. Faithful
--    representations of the current manual seed ACTIVE.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.regulatory_rule (
    rule_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(128) NOT NULL,
    version        INT NOT NULL DEFAULT 1,
    owner          VARCHAR(64),                      -- TUSO | VARAPI | CROSS_TUSO_VARAPI | ...
    rule_kind      VARCHAR(32) NOT NULL DEFAULT 'PRINCIPLE',   -- PRINCIPLE | CONFIG
    statement      TEXT NOT NULL,
    value_json     JSONB,                            -- for CONFIG rules (e.g. {"criticalDays":14})
    implementation VARCHAR(128),
    effective_from DATE,
    effective_to   DATE,
    status         VARCHAR(64) NOT NULL DEFAULT 'PENDING_REGULATOR_APPROVAL',
                                                     -- PENDING_REGULATOR_APPROVAL | ACTIVE | RETIRED
    source_manual  VARCHAR(64),
    source_pages   JSONB,
    approved_by    VARCHAR(255),
    approved_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- ----------------------------------------------------------------------------
-- 4. Application workflow companions: RFI cycle + external council review +
--    fee/payment references + premises/unit targeting + catalogue link.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.application_information_request (
    rfi_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    application_id  UUID NOT NULL REFERENCES tuso.facility_application(application_id),
    requested_by    VARCHAR(255) NOT NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    message         TEXT NOT NULL,
    due_date        DATE,
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN',   -- OPEN | RESPONDED | CLOSED
    response_notes  TEXT,
    response_document_id UUID,
    responded_at    TIMESTAMPTZ,
    closed_by       VARCHAR(255),
    closed_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_rfi_application ON tuso.application_information_request(application_id);

-- External professional-council review (e.g. council PCC on the private route;
-- PCZ acceptance for pharmacy directorship changes). This is a REAL external
-- record — never a boolean, never auto-approved (manual pp.6-7, 10).
CREATE TABLE IF NOT EXISTS tuso.external_council_review (
    review_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    application_id     UUID NOT NULL REFERENCES tuso.facility_application(application_id),
    reviewing_authority VARCHAR(128) NOT NULL,     -- council code (canonical, not free text)
    reference_number   VARCHAR(128),
    submitted_date     DATE,
    decision_date      DATE,
    status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                                   -- PENDING | ENDORSED | REJECTED | NOT_REQUIRED
    conditions         TEXT,
    notes              TEXT,
    evidence_document_id UUID,
    recorded_by        VARCHAR(255) NOT NULL,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_provenance  JSONB
);
CREATE INDEX IF NOT EXISTS idx_council_review_application ON tuso.external_council_review(application_id);

ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS catalogue_code VARCHAR(64);
ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS facility_unit_id BIGINT REFERENCES tuso.facility_unit(id);
ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS premises_id UUID REFERENCES tuso.facility_premises(premises_id);
ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS fee_reference VARCHAR(128);
ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(128);
ALTER TABLE tuso.facility_application ADD COLUMN IF NOT EXISTS application_number VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_application_number ON tuso.facility_application(application_number)
    WHERE application_number IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 5. PIC nomination workflow (pack state machine 3.5). Tuso owns the facility
--    nomination + effective-dated assignment; Varapi supplies the eligibility
--    assessment and remains the professional-registry record. No free-text
--    names; no destructive replacement; no activation without approval.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.pic_nomination (
    nomination_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    facility_unit_id    BIGINT REFERENCES tuso.facility_unit(id),
    provider_public_id  VARCHAR(64) NOT NULL,
    impilo_health_id    UUID,
    state               VARCHAR(64) NOT NULL DEFAULT 'PROPOSED',
        -- PROPOSED | ELIGIBILITY_CHECKED | PRACTITIONER_ACCEPTANCE_PENDING | ACCEPTED
        -- | DECLINED | DISPUTED | REGULATOR_REVIEW_PENDING | APPROVED | REJECTED
        -- | ACTIVATED | WITHDRAWN
    eligibility_snapshot JSONB,                    -- verbatim varapi assessment (per-axis evidence)
    eligibility_result  VARCHAR(32),               -- ELIGIBLE | INELIGIBLE | CONDITIONAL
    eligibility_snapshot_ref VARCHAR(128),         -- varapi snapshot id
    nominated_by        VARCHAR(255) NOT NULL,
    nominated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    practitioner_response VARCHAR(32),             -- ACCEPTED | DECLINED | DISPUTED
    practitioner_response_at TIMESTAMPTZ,
    practitioner_response_notes TEXT,
    review_authority    VARCHAR(128),
    review_reference    VARCHAR(128),
    review_state        VARCHAR(32),               -- PENDING | APPROVED | REJECTED | NOT_REQUIRED
    review_recorded_by  VARCHAR(255),
    review_recorded_at  TIMESTAMPTZ,
    activated_assignment_id BIGINT,                -- tuso.practitioner_in_charge_assignment.id
    linked_application_id UUID REFERENCES tuso.facility_application(application_id),
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_pic_nomination_facility ON tuso.pic_nomination(facility_id);
CREATE INDEX IF NOT EXISTS idx_pic_nomination_provider ON tuso.pic_nomination(provider_public_id);
CREATE INDEX IF NOT EXISTS idx_pic_nomination_state ON tuso.pic_nomination(state);

-- PIC assignment review/compliance state: a credential change never deletes an
-- assignment — it flags it for audited human review.
ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS review_state VARCHAR(32) NOT NULL DEFAULT 'IN_GOOD_STANDING';
    -- IN_GOOD_STANDING | REVIEW_REQUIRED | UNDER_REVIEW | CLEARED
ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS review_reason TEXT;
ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS facility_unit_id BIGINT REFERENCES tuso.facility_unit(id);
ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS predecessor_assignment_id BIGINT;

-- Units can declare that they require their own PIC (manual p.8).
ALTER TABLE tuso.facility_unit ADD COLUMN IF NOT EXISTS pic_required BOOLEAN NOT NULL DEFAULT false;

-- ----------------------------------------------------------------------------
-- 6. Inspection case/visit split + structured per-item responses.
--    facility_inspection remains the CASE; visits carry team/COI/lead truth.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tuso.inspection_visit (
    visit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    inspection_id   UUID NOT NULL REFERENCES tuso.facility_inspection(inspection_id),
    visit_number    INT NOT NULL DEFAULT 1,
    scheduled_date  DATE,
    actual_date     DATE,
    mode            VARCHAR(32) NOT NULL DEFAULT 'ONSITE',    -- ONSITE | DESKTOP | VIRTUAL
    lead_inspector_id VARCHAR(255),
    lead_inspector_name VARCHAR(255),
    team            JSONB,   -- [{actorId,name,role,authority,councilNominee,coiDeclared,coiNotes,expertise}]
    status          VARCHAR(32) NOT NULL DEFAULT 'PLANNED',   -- PLANNED | IN_PROGRESS | COMPLETED | CANCELLED
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_visit_inspection ON tuso.inspection_visit(inspection_id);

CREATE TABLE IF NOT EXISTS tuso.inspection_response (
    response_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    visit_id         UUID NOT NULL REFERENCES tuso.inspection_visit(visit_id),
    template_id      UUID,
    template_version INT,
    item_code        VARCHAR(64) NOT NULL,
    item_section     VARCHAR(128),
    requirement_text TEXT,
    response         VARCHAR(32) NOT NULL,
        -- COMPLIANT | NON_COMPLIANT | NOT_APPLICABLE | NOT_OBSERVED | UNABLE_TO_VERIFY
    measurement      JSONB,
    comments         TEXT,
    evidence_refs    JSONB,
    inspector_id     VARCHAR(255) NOT NULL,
    captured_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       VARCHAR(255),
    UNIQUE (visit_id, item_code)
);
CREATE INDEX IF NOT EXISTS idx_response_visit ON tuso.inspection_response(visit_id);

ALTER TABLE tuso.inspection_finding ADD COLUMN IF NOT EXISTS response_id UUID REFERENCES tuso.inspection_response(response_id);
ALTER TABLE tuso.inspection_finding ADD COLUMN IF NOT EXISTS extension_due_date DATE;
ALTER TABLE tuso.inspection_finding ADD COLUMN IF NOT EXISTS extension_reason TEXT;
ALTER TABLE tuso.inspection_finding ADD COLUMN IF NOT EXISTS recurrence_of_finding_id UUID;

-- ----------------------------------------------------------------------------
-- 7. Checklist template governance: versioned, regulator-approvable, traceable.
-- ----------------------------------------------------------------------------
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS status VARCHAR(64) NOT NULL DEFAULT 'PENDING_REGULATOR_APPROVAL';
    -- DRAFT | PENDING_REGULATOR_APPROVAL | APPROVED | RETIRED
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS source_manual VARCHAR(64);
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS source_pages JSONB;
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255);
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE tuso.inspection_checklist_template ADD COLUMN IF NOT EXISTS supersedes_template_id UUID;

-- Honest backfill: the V006 seed templates are sample/skeleton checklists (3
-- generic items each) — NOT faithful extractions of the current manual's
-- requirement schedules. They remain PENDING_REGULATOR_APPROVAL on fidelity
-- grounds (not because of the manual's year) until manual-derived items are
-- curated and published through template governance.
UPDATE tuso.inspection_checklist_template
   SET status = 'PENDING_REGULATOR_APPROVAL',
       source_manual = 'HPA-2017-V1',
       source_pages = CASE code
           WHEN 'GENERAL-MIN-INITIAL-V1' THEN '[16,17,18,19]'::jsonb
           WHEN 'CLINIC-INITIAL-V1'      THEN '[20,21,22]'::jsonb
           WHEN 'LAB-INITIAL-V1'         THEN '[38,39,40]'::jsonb
           WHEN 'MATERNITY-ROUTINE-V1'   THEN '[30,31]'::jsonb
           ELSE NULL END
 WHERE status = 'PENDING_REGULATOR_APPROVAL' AND source_manual IS NULL;

-- ----------------------------------------------------------------------------
-- 8. Certificate verification + unit-scoped certificates.
-- ----------------------------------------------------------------------------
ALTER TABLE tuso.facility_certificate ADD COLUMN IF NOT EXISTS verification_code VARCHAR(32);
CREATE UNIQUE INDEX IF NOT EXISTS uq_certificate_verification_code ON tuso.facility_certificate(verification_code)
    WHERE verification_code IS NOT NULL;
ALTER TABLE tuso.facility_certificate ADD COLUMN IF NOT EXISTS facility_unit_id BIGINT REFERENCES tuso.facility_unit(id);
ALTER TABLE tuso.facility_certificate ADD COLUMN IF NOT EXISTS conditions TEXT;
ALTER TABLE tuso.facility_certificate ADD COLUMN IF NOT EXISTS status_reason TEXT;

-- Backfill verification codes for existing certificates (deterministic, unique).
UPDATE tuso.facility_certificate
   SET verification_code = UPPER(SUBSTRING(REPLACE(certificate_id::text, '-', '') FROM 1 FOR 12))
 WHERE verification_code IS NULL;

-- Evidence versioning chain on documents.
ALTER TABLE tuso.facility_document ADD COLUMN IF NOT EXISTS supersedes_document_id UUID;

-- ============================================================================
-- SEEDS (HPA-2017-V1 — reference data requiring regulator validation)
-- ============================================================================

INSERT INTO tuso.regulatory_application_type
    (code, display_name, engine_type, route, council_review_required, inspection_required, fee_required,
     manual_route_summary, source_manual, source_page_start, source_page_end)
VALUES
    ('INITIAL_REGISTRATION_PRIVATE', 'Private health-institution initial registration', 'NEW_REGISTRATION',
     'PRIVATE', true, true, true, 'Council PCC route before HPA', 'HPA-2017-V1', 6, 7),
    ('INITIAL_REGISTRATION_PUBLIC_MISSION_LA', 'Government/mission/local-authority initial registration', 'NEW_REGISTRATION',
     'PUBLIC_MISSION_LA', false, true, true, 'Direct HPA route; no Council PCC step in manual', 'HPA-2017-V1', 7, 7),
    ('UNIT_OR_DEPARTMENT_REGISTRATION', 'Additional unit or department registration', 'ADD_UNIT',
     'ANY', false, true, true, 'Separate registration, inspection and fee prior to use', 'HPA-2017-V1', 8, 8),
    ('RELOCATION', 'Change of premises', 'CHANGE_OF_PREMISES',
     'ANY', false, true, true, 'Certificate not transferable; application process restarts', 'HPA-2017-V1', 8, 8),
    ('RENEWAL', 'Certificate renewal', 'RENEWAL',
     'ANY', false, false, true, 'Manual states certificate valid to 31 December of the year (cycle configurable)', 'HPA-2017-V1', 9, 9),
    ('PRACTITIONER_IN_CHARGE_CHANGE', 'Material change: practitioner in charge', 'CHANGE_OF_PRACTITIONER_IN_CHARGE',
     'ANY', true, false, false, 'Requires council approval and material-change evidence', 'HPA-2017-V1', 9, 10),
    ('DIRECTORSHIP_CHANGE_PHARMACY', 'Material change: pharmacy directorship', 'MATERIAL_CHANGE',
     'ANY', true, false, false, 'Manual requires PCZ acceptance before HPA effect', 'HPA-2017-V1', 10, 10),
    ('VOLUNTARY_CLOSURE', 'Voluntary closure', 'VOLUNTARY_CLOSURE_NOTICE',
     'ANY', false, false, false, 'PIC/facility informs HPA in writing', 'HPA-2017-V1', 14, 14)
ON CONFLICT (code) DO NOTHING;

INSERT INTO tuso.inspection_type_reference (code, display_name, purpose, source_manual, source_page) VALUES
    ('INITIAL', 'Initial inspection', 'For institutions applying for registration; assesses category minimum requirements', 'HPA-2017-V1', 10),
    ('ROUTINE', 'Routine inspection', 'For existing institutions; checks whether standards are maintained', 'HPA-2017-V1', 11),
    ('JOINT', 'Joint inspection', 'HPA inspectors and relevant council representatives inspect together', 'HPA-2017-V1', 11),
    ('MULTIDISCIPLINARY', 'Multidisciplinary inspection', 'Relevant councils nominate inspectors based on services, expertise and experience', 'HPA-2017-V1', 12),
    ('INVESTIGATIVE', 'Investigative inspection', 'Triggered by complaints of malpractice or substandard premises; broader investigation permitted', 'HPA-2017-V1', 12),
    ('FOLLOW_UP', 'Follow-up inspection', 'Checks correction of shortfalls from a prior inspection', 'HPA-2017-V1', 13),
    ('VERIFICATION', 'Verification inspection', 'Checks institution registration status, by HPA or jointly with council representatives', 'HPA-2017-V1', 13)
ON CONFLICT (code) DO NOTHING;

INSERT INTO tuso.facility_classification (class_code, facility_type_label, source_manual, source_page) VALUES
    ('A', 'Clinics (up to 50 beds)', 'HPA-2017-V1', 15),
    ('A', 'Consulting rooms (Specialist)', 'HPA-2017-V1', 15),
    ('A', 'Consulting rooms with theatres', 'HPA-2017-V1', 15),
    ('A', 'Dental surgeries (Specialist)', 'HPA-2017-V1', 15),
    ('A', 'Diagnostic X-ray rooms', 'HPA-2017-V1', 15),
    ('A', 'Emergency rooms', 'HPA-2017-V1', 15),
    ('A', 'Hospitals with 101 beds and above', 'HPA-2017-V1', 15),
    ('A', 'Hospital with 51-100 beds', 'HPA-2017-V1', 15),
    ('A', 'Hospital with up to 50 beds', 'HPA-2017-V1', 15),
    ('A', 'Industrial clinics', 'HPA-2017-V1', 15),
    ('A', 'Manufacturing Pharmaceuticals/Wholesalers', 'HPA-2017-V1', 15),
    ('A', 'Maternity homes (outpatients)', 'HPA-2017-V1', 15),
    ('A', 'Maternity hospital', 'HPA-2017-V1', 15),
    ('A', 'Medical research laboratories', 'HPA-2017-V1', 15),
    ('A', 'Multidisciplinary medical laboratories', 'HPA-2017-V1', 15),
    ('A', 'Nursing homes', 'HPA-2017-V1', 15),
    ('A', 'Radiological centres', 'HPA-2017-V1', 15),
    ('A', 'Retail pharmacy', 'HPA-2017-V1', 15),
    ('A', 'Ultra-sound scanning rooms', 'HPA-2017-V1', 15),
    ('B', 'Clinics (out patients only)', 'HPA-2017-V1', 15),
    ('B', 'Optical and dispensing rooms', 'HPA-2017-V1', 15),
    ('B', 'Dental surgeries (general practice)', 'HPA-2017-V1', 15),
    ('B', 'Consulting rooms (general practice)', 'HPA-2017-V1', 15),
    ('B', 'Rehabilitation centres', 'HPA-2017-V1', 15),
    ('B', 'Medical laboratory collection point', 'HPA-2017-V1', 15),
    ('B', 'VCT clinics', 'HPA-2017-V1', 15),
    ('B', 'Mobile clinics', 'HPA-2017-V1', 15),
    ('B', 'Psychological rooms', 'HPA-2017-V1', 15),
    ('B', 'Clinical Social Worker', 'HPA-2017-V1', 15),
    ('B', 'Natural Therapists rooms', 'HPA-2017-V1', 15),
    ('B', 'Natural/Herbal/Organic Manufacturers Wholesalers and Retailers', 'HPA-2017-V1', 15),
    ('B', 'Orthopaedic Technologist rooms', 'HPA-2017-V1', 15),
    ('B', 'Biokinetics rooms', 'HPA-2017-V1', 15),
    ('B', 'Dieticians Private Practice', 'HPA-2017-V1', 15),
    ('B', 'Hospital Food Services Private Practice', 'HPA-2017-V1', 15),
    ('B', 'Single discipline medical laboratories', 'HPA-2017-V1', 15),
    ('C', 'Government, Local Authority and Mission: Clinics (out patients only)', 'HPA-2017-V1', 15),
    ('C', 'Government, Local Authority and Mission: Hospital 51-100 beds', 'HPA-2017-V1', 15),
    ('C', 'Government, Local Authority and Mission: Hospital 101 beds and above', 'HPA-2017-V1', 15)
ON CONFLICT (class_code, facility_type_label) DO NOTHING;

-- Curated principles (16): faithful, page-cited representations of the current manual.
INSERT INTO tuso.regulatory_rule (code, owner, rule_kind, statement, implementation, source_manual, source_pages) VALUES
    ('FACILITY_REQUIRES_REGISTERED_PIC', 'CROSS_TUSO_VARAPI', 'PRINCIPLE', 'A facility application must identify a qualified, registered practitioner in charge with valid professional evidence appropriate to the facility or unit.', 'eligibility_assessment_plus_human_approval', 'HPA-2017-V1', '[6,7,16]'),
    ('PRIVATE_ROUTE_COUNCIL_REVIEW', 'TUSO', 'PRINCIPLE', 'The manual routes private applications through the applicant practitioner''s council Practice Control Committee before HPA registration processing.', 'configurable_external_review_step', 'HPA-2017-V1', '[6,7]'),
    ('PUBLIC_MISSION_LOCAL_AUTHORITY_ROUTE', 'TUSO', 'PRINCIPLE', 'The manual routes government, mission and local-authority applications directly to HPA rather than through council PCC.', 'configurable_route_variant', 'HPA-2017-V1', '[7]'),
    ('NO_OPERATION_BEFORE_REGISTRATION', 'TUSO', 'PRINCIPLE', 'The manual states that applicants should not commence operations before HPA inspection and registration, subject to any separately authorised going-concern exception.', 'status_and_policy_gate_not_clinical_emergency_denial', 'HPA-2017-V1', '[7,18]'),
    ('ADDITIONAL_UNIT_SEPARATE_REGISTRATION', 'TUSO', 'PRINCIPLE', 'Additional departments or units may require separate registration, fee and inspection before use.', 'independent_facility_unit_regulatory_profile', 'HPA-2017-V1', '[8]'),
    ('CERTIFICATE_NOT_TRANSFERABLE_ON_RELOCATION', 'TUSO', 'PRINCIPLE', 'A change of premises restarts the regulatory application context; a certificate must not be silently transferred.', 'relocation_application_and_successor_premises', 'HPA-2017-V1', '[8]'),
    ('SHARED_PREMISES_SUPPORTS_MULTIPLE_REGULATORY_ENTITIES', 'TUSO', 'PRINCIPLE', 'The same physical address can support a partnership registration or separately registered institutions depending on practitioners and councils.', 'premises_entity_separated_from_regulated_entity', 'HPA-2017-V1', '[8,9]'),
    ('ANNUAL_OR_CONFIGURED_RENEWAL', 'TUSO', 'PRINCIPLE', 'Registration certificates are valid for a calendar year, expiring 31 December of the year of issue (manual §3.1); the cycle remains versioned configuration so amendments apply without code change.', 'versioned_renewal_policy', 'HPA-2017-V1', '[9]'),
    ('PIC_CHANGE_REQUIRES_MATERIAL_CHANGE_WORKFLOW', 'CROSS_TUSO_VARAPI', 'PRINCIPLE', 'A change of practitioner in charge requires evidence, professional-council review and an authorised material-change process.', 'effective_dated_assignment_workflow', 'HPA-2017-V1', '[9,10]'),
    ('INSPECTION_TEMPLATE_MATCHES_FACILITY_TYPE', 'TUSO', 'PRINCIPLE', 'Inspectors use requirements specific to the health-institution type.', 'versioned_template_applicability', 'HPA-2017-V1', '[10,11,12]'),
    ('HUMAN_REGULATORY_DECISION', 'TUSO', 'PRINCIPLE', 'Inspection reports are considered by an authorised registration committee or chair as described by policy.', 'explicit_decision_record_no_automatic_licensing', 'HPA-2017-V1', '[10,11,12]'),
    ('SHORTFALL_REMEDIATION_AND_VERIFICATION', 'TUSO', 'PRINCIPLE', 'Shortfalls require corrective action and may require follow-up or later verification; criticality affects timing and outcome.', 'configurable_findings_capa_verification', 'HPA-2017-V1', '[10,11,13,18,19]'),
    ('INSPECTION_TYPE_CATALOGUE', 'TUSO', 'PRINCIPLE', 'Support initial, routine, joint, multidisciplinary, investigative, follow-up and verification inspections.', 'inspection_type_reference_data', 'HPA-2017-V1', '[10,11,12,13]'),
    ('CLOSURE_IS_GOVERNED_ACTION', 'TUSO', 'PRINCIPLE', 'Closure can arise from unregistered operation, public hazard, masquerading practitioner, unresolved minimum requirements or voluntary notice; authority and procedure differ.', 'enforcement_case_and_closure_action', 'HPA-2017-V1', '[13,14]'),
    ('PROVIDER_MUST_HAVE_VALID_PRACTISING_CERTIFICATE', 'VARAPI', 'PRINCIPLE', 'Practitioners operating in facilities must have valid practising credentials and relevant licences.', 'versioned_provider_credential_and_eligibility_status', 'HPA-2017-V1', '[16,17]'),
    ('RECORDS_MAY_BE_ELECTRONIC_OR_HYBRID', 'TUSO_WITH_CLINICAL_SYSTEMS', 'PRINCIPLE', 'The manual permits a blend of electronic and hard-copy records and expects records to be available to authorised practitioners and inspectors.', 'evidence_of_recordkeeping_capability_not_storage_of_clinical_records_in_tuso', 'HPA-2017-V1', '[16,17]')
ON CONFLICT (code, version) DO NOTHING;

-- Engine CONFIG values. Timeframes expressly contained in the current manual
-- seed ACTIVE as current regulatory configuration (versioned; amendable without
-- code change). Values the manual does not provide remain PENDING.
INSERT INTO tuso.regulatory_rule (code, owner, rule_kind, statement, value_json, implementation, source_manual, source_pages) VALUES
    ('REMEDIATION_WINDOW_DAYS', 'TUSO', 'CONFIG',
     'Corrective-action windows by finding severity. Manual §5: practitioners are given from two (2) weeks to a month to attend to shortfalls; critical shortfalls receive the shorter timeframe.',
     '{"CRITICAL": 14, "MAJOR": 30, "MINOR": 30}',
     'configurable_findings_capa_verification', 'HPA-2017-V1', '[10,11,13]'),
    ('RENEWAL_CYCLE_MONTHS', 'TUSO', 'CONFIG',
     'Certificate validity model. Manual §3.1: the Registration Certificate is valid for a calendar year, up to 31 December of that year. Fee amounts (incl. penalties/noncompliance) are per Statutory Instrument 78 of 2017 and are configured separately, never hard-coded.',
     '{"mode": "CALENDAR_YEAR", "months": 12}',
     'versioned_renewal_policy', 'HPA-2017-V1', '[9]'),
    ('RENEWAL_DUE_WINDOW_DAYS', 'TUSO', 'CONFIG',
     'How many days before certificate expiry a facility enters RENEWAL_DUE and reminders begin. The manual does not prescribe a reminder window; this operational value awaits explicit governance approval.',
     '{"days": 90}',
     'versioned_renewal_policy', 'HPA-2017-V1', '[9]')
ON CONFLICT (code, version) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Source-authority activation (programme owner confirmation, 2026-07-11): the
-- 2017 manual is the CURRENT operative HPA manual. Faithful, direct
-- representations activate; each remaining pending row has a substantive
-- reason recorded in its statement (not the publication year).
--   ACTIVE:  16 principle rules + REMEDIATION_WINDOW_DAYS + RENEWAL_CYCLE_MONTHS
--   PENDING: RENEWAL_DUE_WINDOW_DAYS (value not provided by the manual)
--   PENDING: 4 checklist templates (sample/skeleton content — NOT faithful
--            extractions of the manual's requirement schedules; the extracted
--            candidates remain unloaded pending curation)
-- ----------------------------------------------------------------------------
UPDATE tuso.regulatory_rule
   SET status = 'ACTIVE',
       approved_by = 'PROGRAMME_OWNER_SOURCE_AUTHORITY_CONFIRMATION_2026-07-11',
       approved_at = now(),
       effective_from = CURRENT_DATE
 WHERE status = 'PENDING_REGULATOR_APPROVAL'
   AND source_manual = 'HPA-2017-V1'
   AND code <> 'RENEWAL_DUE_WINDOW_DAYS';

UPDATE tuso.regulatory_application_type
   SET validation_status = 'VALIDATED_CURRENT_SOURCE'
 WHERE source_manual = 'HPA-2017-V1'
   AND validation_status = 'REFERENCE_REQUIRES_REGULATOR_VALIDATION';

UPDATE tuso.facility_classification
   SET validation_status = 'VALIDATED_CURRENT_SOURCE'
 WHERE source_manual = 'HPA-2017-V1'
   AND validation_status = 'REFERENCE_REQUIRES_REGULATOR_VALIDATION';

UPDATE tuso.inspection_type_reference
   SET validation_status = 'VALIDATED_CURRENT_SOURCE'
 WHERE source_manual = 'HPA-2017-V1'
   AND validation_status = 'REFERENCE_REQUIRES_REGULATOR_VALIDATION';
