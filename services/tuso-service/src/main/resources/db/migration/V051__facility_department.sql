-- Canonical facility department / organisational-unit entity.
--
-- TUSO is the system of record for facility structure. Until now the only
-- department-shaped table was facility_unit (a regulatory/registration unit),
-- and every downstream department reference (Vashandi vsh_workforce_assignment
-- .department_id, TSHEPO trust headers, the experience-bff work-context
-- resolver) has carried an unvalidated free-text VARCHAR(128) with no FK to
-- anything. facility_department is the canonical identity that those
-- references validate against (see A5 migration/quarantine work).
--
-- Deliberately kept distinct from facility_unit (regulatory registration),
-- service_point (operational sub-unit / queue anchor), and clinical_space
-- (ward/theatre/ICU capacity) — a department is an organisational grouping
-- that MAY contain any of those, not a replacement for them. Link columns are
-- added to each so existing entities can optionally attach to a department
-- without collapsing the distinction between department / service point /
-- ward / clinic / theatre / unit / queue / room.

CREATE TABLE IF NOT EXISTS tuso.facility_department (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id             BIGINT NOT NULL REFERENCES tuso.facility(id),
    tenant_id               UUID NOT NULL,
    department_code         VARCHAR(64) NOT NULL,
    official_name           VARCHAR(255) NOT NULL,
    display_name            VARCHAR(255),
    department_type         VARCHAR(32) NOT NULL DEFAULT 'CLINICAL',
        -- CLINICAL | ADMINISTRATIVE | SUPPORT | VIRTUAL
    parent_department_id    UUID REFERENCES tuso.facility_department(id),
    operating_status        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | PLANNED | INACTIVE | CLOSED
    opened_at               DATE,
    closed_at               DATE,
    head_person_health_id   UUID,
    location_ref            VARCHAR(255),
    contact_json            JSONB,
    escalation_json         JSONB,
    -- provenance: was this row authored directly, or migrated from a legacy
    -- free-text department_id string elsewhere in the platform (A5)?
    provenance              VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
        -- NATIVE | MIGRATED
    source_department_ref   VARCHAR(128),
    metadata                JSONB,
    version                 INTEGER NOT NULL DEFAULT 1,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT chk_facility_department_type
        CHECK (department_type IN ('CLINICAL','ADMINISTRATIVE','SUPPORT','VIRTUAL')),
    CONSTRAINT chk_facility_department_operating_status
        CHECK (operating_status IN ('ACTIVE','PLANNED','INACTIVE','CLOSED')),
    CONSTRAINT chk_facility_department_provenance
        CHECK (provenance IN ('NATIVE','MIGRATED')),
    CONSTRAINT uq_facility_department_code UNIQUE (facility_id, department_code)
);

CREATE INDEX IF NOT EXISTS idx_facility_department_facility ON tuso.facility_department (facility_id);
CREATE INDEX IF NOT EXISTS idx_facility_department_tenant ON tuso.facility_department (tenant_id);
CREATE INDEX IF NOT EXISTS idx_facility_department_parent ON tuso.facility_department (parent_department_id);
CREATE INDEX IF NOT EXISTS idx_facility_department_active ON tuso.facility_department (facility_id) WHERE active;

-- Optional attachment points — nullable, additive, never a behavioural change
-- to the entities they attach to.
ALTER TABLE tuso.facility_unit
    ADD COLUMN IF NOT EXISTS facility_department_id UUID REFERENCES tuso.facility_department(id);
ALTER TABLE tuso.service_point
    ADD COLUMN IF NOT EXISTS facility_department_id UUID REFERENCES tuso.facility_department(id);
ALTER TABLE tuso.clinical_space
    ADD COLUMN IF NOT EXISTS facility_department_id UUID REFERENCES tuso.facility_department(id);

CREATE INDEX IF NOT EXISTS idx_facility_unit_department ON tuso.facility_unit (facility_department_id);
CREATE INDEX IF NOT EXISTS idx_service_point_department ON tuso.service_point (facility_department_id);
CREATE INDEX IF NOT EXISTS idx_clinical_space_department ON tuso.clinical_space (facility_department_id);

-- Let a facility-management appointment scope itself to a department (e.g.
-- "Department Administrator — Radiology — Facility X") instead of only ever
-- the whole facility. Nullable: existing facility-wide appointments are
-- unaffected.
ALTER TABLE tuso.facility_admin_appointment
    ADD COLUMN IF NOT EXISTS facility_department_id UUID REFERENCES tuso.facility_department(id);
CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_department
    ON tuso.facility_admin_appointment (facility_department_id);

COMMENT ON TABLE tuso.facility_department IS
    'Canonical facility department / organisational-unit identity (TUSO SoR). '
    'Referenced by Vashandi work assignments, TSHEPO work-context resolution and '
    'Work Home department-management contexts. Distinct from facility_unit '
    '(regulatory registration), service_point (operational sub-unit) and '
    'clinical_space (ward/theatre/ICU capacity) — those may optionally attach '
    'to a department via facility_department_id, never collapse into it.';
