-- =====================================================================================
-- org-registry :: V006 — Regulatory appointment model (ROM-W1)
-- The login-context anchor for the Regulatory Operating Model. A regulatory appointment
-- binds a verified person to a regulatory organisation in a CLOSED-vocabulary role with a
-- jurisdiction. This is the SoR for "who is appointed" (doctrine §2, ownership ruling R2).
-- Upgrades the org_registry_authorized_representative shape (free role_title, no jurisdiction)
-- without breaking it. Also adds the organisation logo asset reference (bulk-ingestion-spec).
-- =====================================================================================

-- Closed appointment-role vocabulary. A free-text title never grants access (doctrine §2.3).
CREATE TABLE IF NOT EXISTS org_registry.org_registry_appointment_role (
    role_code           VARCHAR(48) PRIMARY KEY,
    label               VARCHAR(128) NOT NULL,
    description         TEXT,
    is_committee_member BOOLEAN NOT NULL DEFAULT FALSE,
    is_oversight        BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INT NOT NULL DEFAULT 100
);

INSERT INTO org_registry.org_registry_appointment_role
    (role_code, label, description, is_committee_member, is_oversight, sort_order) VALUES
    ('REGISTRAR',              'Registrar',               'Head of the council secretariat; management authority.', FALSE, FALSE, 10),
    ('DEPUTY_REGISTRAR',       'Deputy Registrar',        'Deputises for the registrar.',                            FALSE, FALSE, 20),
    ('REGISTRATION_OFFICER',   'Registration Officer',    'Processes registration + renewal applications.',          FALSE, FALSE, 30),
    ('INSPECTOR',              'Inspector',               'Conducts inspections within a jurisdiction.',             FALSE, FALSE, 40),
    ('CPD_OFFICER',            'CPD Officer',             'Adjudicates continuing professional development.',        FALSE, FALSE, 50),
    ('INVESTIGATIONS_OFFICER', 'Investigations Officer',  'Handles complaints and investigations.',                  FALSE, FALSE, 60),
    ('COMMITTEE_MEMBER',       'Committee Member',        'Sits on a council committee; docket-scoped visibility.',  TRUE,  FALSE, 70),
    ('FINANCE_OFFICER',        'Finance Officer',         'Fees, revenue and reconciliation.',                       FALSE, FALSE, 80),
    ('RECORDS_OFFICER',        'Records Officer',         'Records and certification.',                              FALSE, FALSE, 90),
    ('LEGAL_OFFICER',          'Legal Officer',           'Legal and appeals support.',                              FALSE, FALSE, 100),
    ('COUNCIL_CEO',            'Council CEO',             'Chief executive / accounting officer.',                   FALSE, FALSE, 110),
    ('HPA_OVERSIGHT_OFFICER',  'HPA Oversight Officer',   'HPA consolidated oversight (aggregates + granted cases).', FALSE, TRUE,  120),
    ('HPA_INSPECTORATE_OFFICER','HPA Inspectorate Officer','HPA facility inspectorate.',                             FALSE, FALSE, 130)
ON CONFLICT (role_code) DO NOTHING;

-- The appointment itself. Imports land PENDING_VERIFICATION (grants-no-authority law).
CREATE TABLE IF NOT EXISTS org_registry.org_registry_regulatory_appointment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    organization_id     UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    person_health_id    VARCHAR(128) NOT NULL,
    role_code           VARCHAR(48) NOT NULL REFERENCES org_registry.org_registry_appointment_role(role_code),
    jurisdiction_code   VARCHAR(48) NOT NULL DEFAULT 'NATIONAL',
    committee_id        UUID,          -- set for COMMITTEE_MEMBER once committees exist (V008)
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    source              VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
    evidence_ref        VARCHAR(512),
    appointed_by        VARCHAR(128),
    valid_from          DATE,
    valid_to            DATE,
    verified_by         VARCHAR(128),
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reg_appt_status CHECK (status IN
        ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'ENDED', 'REVOKED')),
    CONSTRAINT chk_reg_appt_source CHECK (source IN ('NATIVE', 'INVITATION', 'IMPORT'))
);

-- At most one ACTIVE appointment per (person, org, role) — a second activation must supersede.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_appt_active
    ON org_registry.org_registry_regulatory_appointment (tenant_id, organization_id, person_health_id, role_code)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_reg_appt_person
    ON org_registry.org_registry_regulatory_appointment (tenant_id, person_health_id, status);
CREATE INDEX IF NOT EXISTS idx_reg_appt_org
    ON org_registry.org_registry_regulatory_appointment (organization_id, status);

-- Organisation logo/asset reference (asset upload → document-service object id; bulk-ingestion-spec).
ALTER TABLE org_registry.org_registry_organization
    ADD COLUMN IF NOT EXISTS logo_object_id VARCHAR(255);
