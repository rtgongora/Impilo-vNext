-- =============================================
-- Service : varapi-service
-- Schema  : varapi
-- Flyway  : V002__full_schema.sql
-- Purpose : Expand provider table, add all
--           remaining domain tables, indexes,
--           and deferred foreign keys.
-- =============================================

-- ============================================================
-- 1. ALTER varapi.provider — add new columns
--    (primary_council_id FK added at the end, after councils
--     table exists)
-- ============================================================

ALTER TABLE varapi.provider
    ADD COLUMN provider_public_id   VARCHAR(26)     NOT NULL UNIQUE,
    ADD COLUMN title                VARCHAR(20),
    ADD COLUMN date_of_birth        DATE,
    ADD COLUMN gender               VARCHAR(10),
    ADD COLUMN nationality          VARCHAR(50)     DEFAULT 'ZW',
    ADD COLUMN national_id          VARCHAR(50),
    ADD COLUMN email                VARCHAR(255),
    ADD COLUMN phone                VARCHAR(50),
    ADD COLUMN profession           VARCHAR(100),
    ADD COLUMN cadre                VARCHAR(100),
    ADD COLUMN primary_council_id   BIGINT,
    ADD COLUMN employment_org_id    BIGINT,
    ADD COLUMN profile_photo_ref    VARCHAR(500),
    ADD COLUMN version              INTEGER         DEFAULT 1,
    ADD COLUMN created_by           VARCHAR(255),
    ADD COLUMN updated_by           VARCHAR(255);

-- ============================================================
-- 2. Create remaining tables
-- ============================================================

-- ------------------------------------------------------------
-- provider_contacts
-- ------------------------------------------------------------
CREATE TABLE varapi.provider_contacts (
    id              BIGSERIAL       PRIMARY KEY,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id       UUID            NOT NULL,
    contact_type    VARCHAR(30)     NOT NULL,
    value           VARCHAR(255)    NOT NULL,
    verified        BOOLEAN         DEFAULT false,
    primary_contact BOOLEAN         DEFAULT false,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- provider_identifiers
-- ------------------------------------------------------------
CREATE TABLE varapi.provider_identifiers (
    id                  BIGSERIAL       PRIMARY KEY,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id           UUID            NOT NULL,
    identifier_system   VARCHAR(100)    NOT NULL,  -- e.g. MDPCZ_REG, NMCZ_REG, HPCZ_REG
    identifier_value    VARCHAR(255)    NOT NULL,
    issuing_council_id  BIGINT,
    status              VARCHAR(20)     DEFAULT 'ACTIVE',
    issued_date         DATE,
    expiry_date         DATE,
    created_at          TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (identifier_system, identifier_value)
);

-- ------------------------------------------------------------
-- provider_specialties
-- ------------------------------------------------------------
CREATE TABLE varapi.provider_specialties (
    id                  BIGSERIAL       PRIMARY KEY,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id           UUID            NOT NULL,
    specialty_code      VARCHAR(50)     NOT NULL,
    specialty_name      VARCHAR(255),
    subspecialty_code   VARCHAR(50),
    subspecialty_name   VARCHAR(255),
    zibo_validated      BOOLEAN         DEFAULT false,
    primary_specialty   BOOLEAN         DEFAULT false,
    effective_date      DATE,
    end_date            DATE,
    created_at          TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- councils
-- ------------------------------------------------------------
CREATE TABLE varapi.councils (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    council_code    VARCHAR(50)     NOT NULL UNIQUE,
    name            VARCHAR(255)    NOT NULL,
    council_type    VARCHAR(30)     NOT NULL,  -- HPA, PROFESSIONAL_COUNCIL, ORG_HR
    description     TEXT,
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    website         VARCHAR(500),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- council_users
-- ------------------------------------------------------------
CREATE TABLE varapi.council_users (
    id              BIGSERIAL       PRIMARY KEY,
    council_id      BIGINT          NOT NULL REFERENCES varapi.councils(id),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL,  -- REGISTRAR, REVIEWER, ADMIN
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (council_id, actor_id)
);

-- ------------------------------------------------------------
-- provider_council_affiliations
-- ------------------------------------------------------------
CREATE TABLE varapi.provider_council_affiliations (
    id                  BIGSERIAL       PRIMARY KEY,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider(id),
    council_id          BIGINT          NOT NULL REFERENCES varapi.councils(id),
    tenant_id           UUID            NOT NULL,
    registration_number VARCHAR(100),
    registration_date   DATE,
    status              VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (provider_id, council_id)
);

-- ------------------------------------------------------------
-- licenses
-- ------------------------------------------------------------
CREATE TABLE varapi.licenses (
    id                  BIGSERIAL       PRIMARY KEY,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider(id),
    council_id          BIGINT          REFERENCES varapi.councils(id),
    tenant_id           UUID            NOT NULL,
    license_type        VARCHAR(50)     NOT NULL,  -- PRACTICE, SPECIALIST, TEMPORARY
    license_number      VARCHAR(100),
    status              VARCHAR(30)     NOT NULL,  -- ACTIVE, SUSPENDED, EXPIRED, REVOKED, PENDING_RENEWAL
    valid_from          DATE            NOT NULL,
    valid_to            DATE,
    conditions          TEXT,
    issued_by           VARCHAR(255),
    issued_at           TIMESTAMPTZ,
    suspended_at        TIMESTAMPTZ,
    suspension_reason   TEXT,
    revoked_at          TIMESTAMPTZ,
    revocation_reason   TEXT,
    version             INTEGER         DEFAULT 1,
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- privileges
-- ------------------------------------------------------------
CREATE TABLE varapi.privileges (
    id                      BIGSERIAL       PRIMARY KEY,
    provider_id             BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id               UUID            NOT NULL,
    facility_id             BIGINT,                    -- TUSO facility reference
    workspace_id            UUID,                      -- TUSO workspace reference
    scope                   VARCHAR(100)    NOT NULL,  -- e.g. GENERAL_PRACTICE, SURGERY, PRESCRIBE
    privilege_type          VARCHAR(50)     NOT NULL,  -- CLINICAL, ADMINISTRATIVE, SUPERVISORY
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REVOKED, SUSPENDED
    granted_by              VARCHAR(255),
    granted_at              TIMESTAMPTZ,
    revoked_by              VARCHAR(255),
    revoked_at              TIMESTAMPTZ,
    revocation_reason       TEXT,
    valid_from              DATE,
    valid_to                DATE,
    supervising_provider_id BIGINT          REFERENCES varapi.provider(id),
    approval_ref            VARCHAR(255),
    notes                   TEXT,
    created_at              TIMESTAMPTZ     DEFAULT now(),
    updated_at              TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- privilege_approvals
-- ------------------------------------------------------------
CREATE TABLE varapi.privilege_approvals (
    id              BIGSERIAL       PRIMARY KEY,
    privilege_id    BIGINT          NOT NULL REFERENCES varapi.privileges(id),
    tenant_id       UUID            NOT NULL,
    action          VARCHAR(30)     NOT NULL,  -- SUBMITTED, APPROVED, REJECTED, ESCALATED
    actor_id        VARCHAR(255)    NOT NULL,
    actor_role      VARCHAR(50),
    reason          TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- org_hr_units
-- ------------------------------------------------------------
CREATE TABLE varapi.org_hr_units (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    org_name        VARCHAR(255)    NOT NULL,
    org_code        VARCHAR(50)     NOT NULL UNIQUE,
    council_id      BIGINT          REFERENCES varapi.councils(id),  -- virtual council for this org
    approval_chain  JSONB,
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- documents
-- ------------------------------------------------------------
CREATE TABLE varapi.documents (
    id              BIGSERIAL       PRIMARY KEY,
    provider_id     BIGINT          REFERENCES varapi.provider(id),
    council_id      BIGINT          REFERENCES varapi.councils(id),
    tenant_id       UUID            NOT NULL,
    document_type   VARCHAR(50)     NOT NULL,  -- CERTIFICATE, ID_DOCUMENT, QUALIFICATION, CPD_EVIDENCE
    file_name       VARCHAR(255),
    content_type    VARCHAR(100),
    storage_ref     VARCHAR(500)    NOT NULL,  -- object storage path
    file_size_bytes BIGINT,
    checksum        VARCHAR(128),
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    uploaded_by     VARCHAR(255),
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- provider_tokens
-- ------------------------------------------------------------
CREATE TABLE varapi.provider_tokens (
    id                  BIGSERIAL       PRIMARY KEY,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id           UUID            NOT NULL,
    lookup_hash         VARCHAR(128)    NOT NULL UNIQUE,
    argon2_verifier     VARCHAR(512)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, ROTATED, REVOKED
    issued_at           TIMESTAMPTZ     DEFAULT now(),
    rotated_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    issued_by           VARCHAR(255),
    rotation_reason     TEXT,
    previous_token_id   BIGINT          REFERENCES varapi.provider_tokens(id)
);

-- ------------------------------------------------------------
-- biometric_bindings
-- ------------------------------------------------------------
CREATE TABLE varapi.biometric_bindings (
    id              BIGSERIAL       PRIMARY KEY,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id       UUID            NOT NULL,
    biometric_ref   VARCHAR(255)    NOT NULL,  -- external biometric system reference
    biometric_type  VARCHAR(30)     DEFAULT 'FINGERPRINT',
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    bound_at        TIMESTAMPTZ     DEFAULT now(),
    unbound_at      TIMESTAMPTZ,
    bound_by        VARCHAR(255)
);

-- ------------------------------------------------------------
-- reconciliation_cases
-- ------------------------------------------------------------
CREATE TABLE varapi.reconciliation_cases (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    council_id      BIGINT          REFERENCES varapi.councils(id),
    source_system   VARCHAR(100)    NOT NULL,
    source_ref      VARCHAR(255),
    provider_id     BIGINT          REFERENCES varapi.provider(id),
    case_type       VARCHAR(50)     NOT NULL,  -- NEW_IMPORT, UPDATE_CONFLICT, DUPLICATE, STATUS_MISMATCH
    status          VARCHAR(30)     NOT NULL DEFAULT 'OPEN',  -- OPEN, RESOLVED, REJECTED, DEFERRED
    payload         JSONB           NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- reconciliation_actions
-- ------------------------------------------------------------
CREATE TABLE varapi.reconciliation_actions (
    id              BIGSERIAL       PRIMARY KEY,
    case_id         BIGINT          NOT NULL REFERENCES varapi.reconciliation_cases(id),
    tenant_id       UUID            NOT NULL,
    action          VARCHAR(30)     NOT NULL,  -- ACCEPT, REJECT, MERGE, DEFER, ESCALATE
    actor_id        VARCHAR(255)    NOT NULL,
    reason          TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- cpd_cycles
-- ------------------------------------------------------------
CREATE TABLE varapi.cpd_cycles (
    id              BIGSERIAL       PRIMARY KEY,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider(id),
    council_id      BIGINT          REFERENCES varapi.councils(id),
    tenant_id       UUID            NOT NULL,
    cycle_name      VARCHAR(100)    NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    required_points INTEGER         NOT NULL DEFAULT 0,
    earned_points   INTEGER         NOT NULL DEFAULT 0,
    status          VARCHAR(20)     DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS, COMPLETED, EXPIRED, EXEMPTED
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- cpd_events
-- ------------------------------------------------------------
CREATE TABLE varapi.cpd_events (
    id              BIGSERIAL       PRIMARY KEY,
    cycle_id        BIGINT          NOT NULL REFERENCES varapi.cpd_cycles(id),
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider(id),
    tenant_id       UUID            NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,  -- COURSE, WORKSHOP, CONFERENCE, PUBLICATION, SUPERVISION
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    points_awarded  INTEGER         NOT NULL DEFAULT 0,
    event_date      DATE,
    external_ref    VARCHAR(255),   -- Moodle course ID etc
    verified        BOOLEAN         DEFAULT false,
    verified_by     VARCHAR(255),
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- cpd_evidence
-- ------------------------------------------------------------
CREATE TABLE varapi.cpd_evidence (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        BIGINT          NOT NULL REFERENCES varapi.cpd_events(id),
    tenant_id       UUID            NOT NULL,
    document_id     BIGINT          REFERENCES varapi.documents(id),
    evidence_type   VARCHAR(50),    -- CERTIFICATE, ATTENDANCE, TRANSCRIPT
    notes           TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- disciplinary_actions
-- ------------------------------------------------------------
CREATE TABLE varapi.disciplinary_actions (
    id              BIGSERIAL       PRIMARY KEY,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider(id),
    council_id      BIGINT          REFERENCES varapi.councils(id),
    tenant_id       UUID            NOT NULL,
    action_type     VARCHAR(50)     NOT NULL,  -- WARNING, REPRIMAND, FINE, SUSPENSION, REVOCATION, CONDITION
    description     TEXT            NOT NULL,
    severity        VARCHAR(20),    -- LOW, MEDIUM, HIGH, CRITICAL
    status          VARCHAR(30)     DEFAULT 'ACTIVE',  -- ACTIVE, COMPLETED, APPEALED, OVERTURNED
    imposed_date    DATE,
    expiry_date     DATE,
    conditions      TEXT,
    imposed_by      VARCHAR(255),
    appeal_ref      VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

-- ------------------------------------------------------------
-- event_outbox (reliable Kafka publishing via outbox pattern)
-- ------------------------------------------------------------
CREATE TABLE varapi.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    published_at    TIMESTAMPTZ,    -- NULL = unpublished
    created_at      TIMESTAMPTZ     DEFAULT now()
);

-- ============================================================
-- 3. Indexes
-- ============================================================

-- provider
CREATE INDEX idx_provider_public_id         ON varapi.provider (provider_public_id);
CREATE INDEX idx_provider_profession        ON varapi.provider (tenant_id, profession);
CREATE INDEX idx_provider_council           ON varapi.provider (primary_council_id);

-- provider_contacts
CREATE INDEX idx_provider_contacts_provider ON varapi.provider_contacts (provider_id);

-- provider_identifiers
CREATE INDEX idx_provider_identifiers_provider ON varapi.provider_identifiers (provider_id);

-- provider_specialties
CREATE INDEX idx_provider_specialties_provider ON varapi.provider_specialties (provider_id);

-- licenses
CREATE INDEX idx_licenses_provider_status   ON varapi.licenses (provider_id, status);
CREATE INDEX idx_licenses_valid_to          ON varapi.licenses (valid_to) WHERE status = 'ACTIVE';

-- privileges
CREATE INDEX idx_privileges_provider        ON varapi.privileges (provider_id, status);
CREATE INDEX idx_privileges_facility        ON varapi.privileges (facility_id, status);

-- provider_tokens
CREATE INDEX idx_provider_tokens_provider   ON varapi.provider_tokens (provider_id, status);

-- biometric_bindings
CREATE INDEX idx_biometric_provider         ON varapi.biometric_bindings (provider_id, status);

-- reconciliation_cases
CREATE INDEX idx_reconciliation_status      ON varapi.reconciliation_cases (tenant_id, status);

-- cpd_cycles
CREATE INDEX idx_cpd_cycles_provider        ON varapi.cpd_cycles (provider_id, status);

-- cpd_events
CREATE INDEX idx_cpd_events_cycle           ON varapi.cpd_events (cycle_id);

-- disciplinary_actions
CREATE INDEX idx_disciplinary_provider      ON varapi.disciplinary_actions (provider_id, status);

-- event_outbox (poll for unpublished events)
CREATE INDEX idx_outbox_unpublished         ON varapi.event_outbox (created_at) WHERE published_at IS NULL;

-- ============================================================
-- 4. Deferred FK: provider.primary_council_id → councils
--    (councils table now exists)
-- ============================================================

ALTER TABLE varapi.provider
    ADD CONSTRAINT fk_provider_primary_council
    FOREIGN KEY (primary_council_id) REFERENCES varapi.councils(id);
