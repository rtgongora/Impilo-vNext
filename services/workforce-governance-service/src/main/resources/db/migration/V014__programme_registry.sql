-- Canonical health-programme registry.
--
-- Health programmes (HIV, TB, malaria, maternal/newborn, immunisation, mental
-- health, NCDs, emergency preparedness, quality & patient safety, ...) have
-- never had a system of record. Every service that carries a programme_id
-- (experience-bff, vashandi, tshepo-identity, tshepo-authz, msika-apps, rito,
-- simba, live, telemonitoring, integration-hub, tshepo legacy) stores it as
-- an unvalidated free-text VARCHAR with no referent (see A5 migration work).
--
-- Ownership: workforce-governance-service already declares
-- AssignmentTargetType.PROGRAM, JurisdictionType.PROGRAM_REGION and
-- RoleCategory.PROGRAMMATIC (V001) — it is the organisational-authority
-- service and is structurally prepared to own this. No other service in
-- docs/registry/services-registry.yaml claims programme ownership (only
-- simba-service's *wellness* programme participation, a different concept).
--
-- Programme appointments deliberately reuse wgv_assignment
-- (target_type='PROGRAM', target_id=programme_id) rather than a parallel
-- appointment table — that is exactly the mechanism PROGRAM already exists
-- for. AssignmentService validates target_id against wgv_programme when
-- target_type='PROGRAM' (see AssignmentService.assertProgrammeTargetValid).

CREATE TABLE IF NOT EXISTS wgv_programme (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    programme_code              VARCHAR(64) NOT NULL,
    name                        VARCHAR(512) NOT NULL,
    programme_type              VARCHAR(64) NOT NULL,
        -- e.g. HIV | TB | MALARIA | MATERNAL_NEWBORN | IMMUNISATION |
        -- MENTAL_HEALTH | NCD | EMERGENCY_PREPAREDNESS | QUALITY_SAFETY | OTHER
    status                      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | SUSPENDED | CLOSED | ARCHIVED
    scope_level                 VARCHAR(32) NOT NULL,
        -- NATIONAL | PROVINCIAL | DISTRICT | LOCAL
    parent_programme_id         UUID REFERENCES wgv_programme (id),
    responsible_organisation_id UUID REFERENCES wgv_organisation (id),
    accountable_office          VARCHAR(512),
    valid_from                  DATE,
    valid_to                    DATE,
    description                 TEXT,
    provenance                  VARCHAR(32) NOT NULL DEFAULT 'NATIVE',
        -- NATIVE | MIGRATED — MIGRATED rows trace back to a legacy free-text
        -- programme_id via wgv_programme_source_reference (A5)
    metadata                    TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    CONSTRAINT chk_wgv_programme_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED','ARCHIVED')),
    CONSTRAINT chk_wgv_programme_scope_level CHECK (scope_level IN ('NATIONAL','PROVINCIAL','DISTRICT','LOCAL')),
    CONSTRAINT chk_wgv_programme_provenance CHECK (provenance IN ('NATIVE','MIGRATED')),
    CONSTRAINT uq_wgv_programme_code UNIQUE (tenant_id, programme_code)
);

CREATE INDEX IF NOT EXISTS idx_wgv_programme_status ON wgv_programme (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_parent ON wgv_programme (parent_programme_id);

-- Participating jurisdictions (national/provincial/district reach).
CREATE TABLE IF NOT EXISTS wgv_programme_jurisdiction (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    programme_id    UUID NOT NULL REFERENCES wgv_programme (id),
    jurisdiction_id UUID NOT NULL REFERENCES wgv_jurisdiction (id),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_programme_jurisdiction UNIQUE (programme_id, jurisdiction_id)
);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_jurisdiction_programme ON wgv_programme_jurisdiction (programme_id);

-- Participating facilities. facility_id is the numeric TUSO id (cross-service,
-- no FK) — validated at write time by the admin service via the TUSO client
-- in a later wave; kept as an opaque reference here, consistent with how
-- wgv_facility_regulator_relationship already stores facility identifiers.
CREATE TABLE IF NOT EXISTS wgv_programme_facility (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    programme_id    UUID NOT NULL REFERENCES wgv_programme (id),
    facility_id     VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date      DATE,
    end_date        DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_programme_facility UNIQUE (programme_id, facility_id)
);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_facility_programme ON wgv_programme_facility (programme_id);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_facility_facility ON wgv_programme_facility (facility_id);

-- Supported services / pathways (free-text service codes; the programme
-- doesn't own a services catalogue, it declares which service codes it
-- covers).
CREATE TABLE IF NOT EXISTS wgv_programme_service (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    programme_id    UUID NOT NULL REFERENCES wgv_programme (id),
    service_code    VARCHAR(128) NOT NULL,
    service_label   VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_programme_service UNIQUE (programme_id, service_code)
);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_service_programme ON wgv_programme_service (programme_id);

-- Traceability for rows created by the A5 migration/quarantine sweep, mapping
-- a legacy free-text programme_id string (from any of the 11 referencing
-- services) to the canonical programme it was resolved into.
CREATE TABLE IF NOT EXISTS wgv_programme_source_reference (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    programme_id        UUID NOT NULL REFERENCES wgv_programme (id),
    source_service      VARCHAR(128) NOT NULL,
    source_value        VARCHAR(255) NOT NULL,
    resolved_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_by         VARCHAR(255),
    CONSTRAINT uq_wgv_programme_source_reference UNIQUE (source_service, source_value)
);
CREATE INDEX IF NOT EXISTS idx_wgv_programme_source_reference_programme ON wgv_programme_source_reference (programme_id);

COMMENT ON TABLE wgv_programme IS
    'Canonical health-programme registry (WGV SoR). Programme appointments are '
    'wgv_assignment rows with target_type=PROGRAM and target_id=this table''s id; '
    'AssignmentService validates that reference at write time.';
