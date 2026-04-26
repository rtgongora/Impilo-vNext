-- =============================================
-- Service : varapi-service
-- Schema  : varapi
-- Flyway  : V005__provider_lifecycle_extended.sql
-- Purpose : Provider lifecycle extensions for
--           registration, qualification, affiliation,
--           practice context, compliance, certificates,
--           disciplinary, committee review, and status history
-- =============================================

-- ============================================================
-- 1. ENUMS (as check constraints on existing/new tables)
-- ============================================================

-- Provider lifecycle status
DO $$ BEGIN
    CREATE TYPE varapi.provider_lifecycle_status AS ENUM (
        'DRAFT',
        'APPLICATION_IN_PROGRESS',
        'UNDER_VERIFICATION',
        'PENDING_REVIEW',
        'PENDING_COMMITTEE_REVIEW',
        'REGISTERED',
        'LICENCED_ACTIVE',
        'LICENCE_DUE_FOR_RENEWAL',
        'RENEWAL_IN_PROGRESS',
        'RESTRICTED',
        'SUSPENDED',
        'LAPSED',
        'RESTORATION_IN_PROGRESS',
        'RETIRED',
        'DECEASED',
        'REMOVED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Application types
DO $$ BEGIN
    CREATE TYPE varapi.application_type AS ENUM (
        'NEW_REGISTRATION',
        'ANNUAL_RENEWAL',
        'RESTORATION',
        'SPECIALTY_RECOGNITION',
        'TEMPORARY_AUTHORITY_TO_PRACTISE',
        'MATERIAL_CHANGE',
        'CHANGE_OF_NAME',
        'CHANGE_OF_PRIMARY_COUNCIL_DETAILS',
        'REACTIVATION',
        'VIRTUAL_PRACTICE_AUTHORISATION',
        'COMMUNITY_PRACTICE_AUTHORISATION'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Application workflow state
DO $$ BEGIN
    CREATE TYPE varapi.application_workflow_state AS ENUM (
        'DRAFT',
        'SUBMITTED',
        'UNDER_ADMIN_REVIEW',
        'AWAITING_DOCUMENTS',
        'AWAITING_VERIFICATION',
        'AWAITING_FEE',
        'READY_FOR_REVIEW',
        'READY_FOR_COMMITTEE',
        'DECIDED_APPROVED',
        'DECIDED_REJECTED',
        'DECIDED_DEFERRED',
        'CLOSED_OUT'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Document types
DO $$ BEGIN
    CREATE TYPE varapi.document_type AS ENUM (
        'NATIONAL_ID',
        'PASSPORT',
        'QUALIFICATION_CERTIFICATE',
        'ACADEMIC_TRANSCRIPT',
        'INTERNSHIP_EVIDENCE',
        'PRACTISING_CERTIFICATE',
        'SPECIALIST_CERTIFICATE',
        'GOOD_STANDING_LETTER',
        'CPD_EVIDENCE',
        'NAME_CHANGE_DOCUMENT',
        'OTHER_SUPPORTING_DOCUMENT'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Verification status
DO $$ BEGIN
    CREATE TYPE varapi.verification_status AS ENUM (
        'PENDING',
        'VERIFIED',
        'FAILED',
        'NOT_REQUIRED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Affiliation types
DO $$ BEGIN
    CREATE TYPE varapi.affiliation_type AS ENUM (
        'EMPLOYED_BY',
        'CONTRACTED_BY',
        'PRIVILEGED_AT',
        'VISITING_AT',
        'PRACTITIONER_IN_CHARGE_AT',
        'ON_CALL_FOR',
        'COMMUNITY_LINKED_TO',
        'VIRTUAL_PROVIDER_FOR',
        'OUTREACH_PROVIDER_FOR',
        'SUPERVISING_FOR'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Practice context types
DO $$ BEGIN
    CREATE TYPE varapi.practice_context_type AS ENUM (
        'FACILITY',
        'COMMUNITY',
        'OUTREACH',
        'MOBILE',
        'VIRTUAL',
        'HYBRID'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Compliance action types
DO $$ BEGIN
    CREATE TYPE varapi.compliance_action_type AS ENUM (
        'MISSING_DOCUMENT',
        'RENEWAL_DUE',
        'VERIFICATION_PENDING',
        'CPD_REQUIRED',
        'GOOD_STANDING_REQUIRED',
        'AFFILIATION_REVIEW_REQUIRED',
        'LICENCE_RESTRICTION_CONDITION',
        'OTHER'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Certificate types
DO $$ BEGIN
    CREATE TYPE varapi.certificate_type AS ENUM (
        'PRACTISING_CERTIFICATE',
        'REGISTRATION_CERTIFICATE',
        'SPECIALTY_CERTIFICATE',
        'GOOD_STANDING',
        'LETTER_OF_GOOD_STANDING'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Disciplinary trigger types
DO $$ BEGIN
    CREATE TYPE varapi.disciplinary_trigger_type AS ENUM (
        'COMPLAINT',
        'REGULATORY_BREACH',
        'LICENCE_NONCOMPLIANCE',
        'MISREPRESENTATION',
        'OTHER'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Committee types
DO $$ BEGIN
    CREATE TYPE varapi.committee_type AS ENUM (
        'REGISTRATION_COMMITTEE',
        'LICENSING_COMMITTEE',
        'DISCIPLINARY_COMMITTEE',
        'SPECIALTY_BOARD',
        'APPEALS_PANEL'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- ============================================================
-- 2. PROVIDER_APPLICATIONS table
-- ============================================================

CREATE TABLE varapi.provider_applications (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    application_type        VARCHAR(50)     NOT NULL,
    workflow_state          VARCHAR(50)     NOT NULL DEFAULT 'DRAFT',
    priority                VARCHAR(20)     DEFAULT 'NORMAL',
    fee_state               VARCHAR(20)     DEFAULT 'PENDING',
    verification_state      VARCHAR(20)     DEFAULT 'PENDING',
    committee_state         VARCHAR(50),
    final_decision          VARCHAR(50),
    decision_date           DATE,
    authority_route         VARCHAR(255),
    submitted_at            TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    notes                   TEXT,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255)
);

CREATE INDEX idx_provider_applications_provider ON varapi.provider_applications(provider_id);
CREATE INDEX idx_provider_applications_workflow ON varapi.provider_applications(workflow_state);
CREATE INDEX idx_provider_applications_type ON varapi.provider_applications(application_type);

-- ============================================================
-- 3. PROVIDER_QUALIFICATIONS table
-- ============================================================

CREATE TABLE varapi.provider_qualifications (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    qualification_type      VARCHAR(50)     NOT NULL,
    title                   VARCHAR(255)    NOT NULL,
    institution_name        VARCHAR(255)    NOT NULL,
    country                 VARCHAR(100)    NOT NULL,
    award_date              DATE,
    verification_status    VARCHAR(20)     DEFAULT 'PENDING',
    verified_by             VARCHAR(255),
    verified_at             TIMESTAMPTZ,
    evidence_document_id    BIGINT,
    remarks                 TEXT,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_qualifications_provider ON varapi.provider_qualifications(provider_id);
CREATE INDEX idx_provider_qualifications_verification ON varapi.provider_qualifications(verification_status);

-- ============================================================
-- 4. PROVIDER_DOCUMENTS table (extended from existing)
-- ============================================================

CREATE TABLE IF NOT EXISTS varapi.provider_documents (
    LIKE varapi.documents INCLUDING ALL
);

ALTER TABLE varapi.provider_documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(50);
ALTER TABLE varapi.provider_documents ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE varapi.provider_documents ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(255);
ALTER TABLE varapi.provider_documents ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
ALTER TABLE varapi.provider_documents ADD COLUMN IF NOT EXISTS application_id BIGINT REFERENCES varapi.provider_applications(id);

-- ============================================================
-- 5. PROVIDER_AFFILIATIONS table (separate from privileges)
-- ============================================================

CREATE TABLE varapi.provider_affiliations (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    facility_id             BIGINT,                     -- TUSO facility reference
    affiliation_type        VARCHAR(50)     NOT NULL,
    role_title              VARCHAR(100),
    start_date              DATE,
    end_date                DATE,
    status                  VARCHAR(20)     DEFAULT 'PROPOSED',
    approval_state         VARCHAR(20)     DEFAULT 'PENDING',
    primary_flag            BOOLEAN         DEFAULT false,
    linked_authority_decision VARCHAR(255),
    notes                   TEXT,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255)
);

CREATE INDEX idx_provider_affiliations_provider ON varapi.provider_affiliations(provider_id);
CREATE INDEX idx_provider_affiliations_facility ON varapi.provider_affiliations(facility_id);
CREATE INDEX idx_provider_affiliations_status ON varapi.provider_affiliations(status);

-- ============================================================
-- 6. PROVIDER_PRACTICE_CONTEXTS table
-- ============================================================

CREATE TABLE varapi.provider_practice_contexts (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    context_type            VARCHAR(50)     NOT NULL,
    linked_facility_id      BIGINT,
    service_scope_summary   TEXT,
    start_date              DATE,
    end_date                DATE,
    status                  VARCHAR(20)     DEFAULT 'REQUESTED',
    authorisation_basis     VARCHAR(255),
    approved_by             VARCHAR(255),
    decision_date           DATE,
    notes                   TEXT,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_practice_contexts_provider ON varapi.provider_practice_contexts(provider_id);
CREATE INDEX idx_provider_practice_contexts_type ON varapi.provider_practice_contexts(context_type);
CREATE INDEX idx_provider_practice_contexts_status ON varapi.provider_practice_contexts(status);

-- ============================================================
-- 7. PROVIDER_COMPLIANCE_ACTIONS table
-- ============================================================

CREATE TABLE varapi.provider_compliance_actions (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    related_application_id  BIGINT          REFERENCES varapi.provider_applications(id),
    action_type             VARCHAR(50)     NOT NULL,
    description             TEXT,
    owner                   VARCHAR(255),
    due_date                DATE,
    status                  VARCHAR(20)     DEFAULT 'OPEN',
    completion_notes         TEXT,
    verified_by             VARCHAR(255),
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_compliance_actions_provider ON varapi.provider_compliance_actions(provider_id);
CREATE INDEX idx_provider_compliance_actions_type ON varapi.provider_compliance_actions(action_type);
CREATE INDEX idx_provider_compliance_actions_status ON varapi.provider_compliance_actions(status);

-- ============================================================
-- 8. PROVIDER_CERTIFICATES table
-- ============================================================

CREATE TABLE varapi.provider_certificates (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    certificate_type        VARCHAR(50)     NOT NULL,
    issue_date              DATE            NOT NULL,
    expiry_date             DATE,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    issued_under_authority   VARCHAR(255),
    digital_artifact_ref   VARCHAR(500),
    supersedes_certificate_id BIGINT,
    notes                   TEXT,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_certificates_provider ON varapi.provider_certificates(provider_id);
CREATE INDEX idx_provider_certificates_type ON varapi.provider_certificates(certificate_type);
CREATE INDEX idx_provider_certificates_status ON varapi.provider_certificates(status);

-- ============================================================
-- 9. PROVIDER_STATUS_HISTORY table
-- ============================================================

CREATE TABLE varapi.provider_status_history (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    previous_status         VARCHAR(50),
    new_status              VARCHAR(50)     NOT NULL,
    reason                  TEXT,
    changed_by              VARCHAR(255),
    authority_context      VARCHAR(255),
    changed_at              TIMESTAMPTZ     DEFAULT now(),
    metadata                JSONB
);

CREATE INDEX idx_provider_status_history_provider ON varapi.provider_status_history(provider_id);
CREATE INDEX idx_provider_status_history_changed_at ON varapi.provider_status_history(changed_at);

-- ============================================================
-- 10. PROVIDER_COMMITTEE_REVIEWS table
-- ============================================================

CREATE TABLE varapi.provider_committee_reviews (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    related_application_id  BIGINT          REFERENCES varapi.provider_applications(id),
    case_id                 BIGINT,
    committee_type          VARCHAR(50)     NOT NULL,
    scheduled_date          DATE,
    decision                VARCHAR(50),
    notes                   TEXT,
    resolution_reference    VARCHAR(255),
    reviewed_by            VARCHAR(255),
    reviewed_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_committee_reviews_provider ON varapi.provider_committee_reviews(provider_id);
CREATE INDEX idx_provider_committee_reviews_case ON varapi.provider_committee_reviews(case_id);

-- ============================================================
-- 11. PROVIDER_DISCIPLINARY_CASES table
-- ============================================================

CREATE TABLE varapi.provider_disciplinary_cases (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    trigger_type            VARCHAR(50)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    opened_date             DATE,
    route_to_authority      VARCHAR(255),
    summary                 TEXT,
    linked_evidence         JSONB,
    final_recommendation    TEXT,
    final_decision          VARCHAR(50),
    final_decision_date     DATE,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_disciplinary_cases_provider ON varapi.provider_disciplinary_cases(provider_id);
CREATE INDEX idx_provider_disciplinary_cases_status ON varapi.provider_disciplinary_cases(status);

-- ============================================================
-- 12. PRACTITIONER_IN_CHARGE_ASSIGNMENTS table
--    (source-of-truth in VARAPI, mirrored to TUSO)
-- ============================================================

CREATE TABLE varapi.practitioner_in_charge_assignments (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    facility_id             BIGINT          NOT NULL,               -- TUSO facility reference
    assignment_type         VARCHAR(50)     NOT NULL DEFAULT 'PRACTITIONER_IN_CHARGE',
    start_date              DATE,
    end_date                DATE,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    approval_state          VARCHAR(20)     DEFAULT 'PENDING',
    source_council_reference VARCHAR(255),
    decision_reference      VARCHAR(255),
    notes                   TEXT,
    version                 INTEGER         DEFAULT 1,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255)
);

CREATE INDEX idx_pic_assignments_provider ON varapi.practitioner_in_charge_assignments(provider_id);
CREATE INDEX idx_pic_assignments_facility ON varapi.practitioner_in_charge_assignments(facility_id);
CREATE INDEX idx_pic_assignments_status ON varapi.practitioner_in_charge_assignments(status);

-- ============================================================
-- 13. Add lifecycle_status to provider
-- ============================================================

ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(50) DEFAULT 'REGISTERED';
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS licence_status VARCHAR(50);
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS professional_standing_status VARCHAR(50);
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS previous_names VARCHAR(255);
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS middle_name VARCHAR(255);
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS active_flag BOOLEAN DEFAULT true;
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS effective_from DATE;
ALTER TABLE varapi.provider ADD COLUMN IF NOT EXISTS effective_to DATE;

-- ============================================================
-- 14. Add foreign key for primary_council_id
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_provider_primary_council'
          AND conrelid = 'varapi.provider'::regclass
    ) THEN
        ALTER TABLE varapi.provider
            ADD CONSTRAINT fk_provider_primary_council
            FOREIGN KEY (primary_council_id) REFERENCES varapi.councils(id) ON DELETE SET NULL;
    END IF;
END
$$;
