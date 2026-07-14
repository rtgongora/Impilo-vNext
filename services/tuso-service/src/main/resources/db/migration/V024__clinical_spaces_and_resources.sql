-- tuso-service V024 — first-class clinical spaces + theatre resources (Wave 2)
-- TUSO is the REGISTRY / capacity source of truth for physical care spaces and
-- theatre resources. Explicit split of ownership:
--   * tuso.bed = REGISTRY / capacity SoR (what beds exist).
--     inpatient.bed stays the OCCUPANCY SoR (who is in a bed right now).
--   * tuso.equipment_asset = registry presence; the asset-registry service stays
--     the live-readiness SoR — we do not duplicate its maintenance workflow.
-- A theatre clinical_space is 1:1 with a service_point (type=THEATRE) so the
-- existing booking / readiness seams keep working unchanged.

CREATE TABLE IF NOT EXISTS tuso.clinical_space (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    service_point_id    UUID REFERENCES tuso.service_point(id),  -- theatre 1:1 with a THEATRE service_point
    name                VARCHAR(255) NOT NULL,
    space_type          VARCHAR(32) NOT NULL DEFAULT 'OPERATING_THEATRE',
        -- OPERATING_THEATRE | PACU | ICU | HDU | WARD
    capacity            INTEGER NOT NULL DEFAULT 1,
    operating_hours     JSONB NOT NULL DEFAULT '{}'::jsonb,
    capability_tags     JSONB NOT NULL DEFAULT '[]'::jsonb,  -- GENERAL | OBSTETRIC | ORTHOPAEDIC | EMERGENCY
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | CLOSED | MAINTENANCE
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_clinical_space_type
        CHECK (space_type IN ('OPERATING_THEATRE','PACU','ICU','HDU','WARD')),
    CONSTRAINT uq_clinical_space_name UNIQUE (tenant_id, facility_id, name)
);
CREATE INDEX IF NOT EXISTS idx_clinical_space_facility_type
    ON tuso.clinical_space (tenant_id, facility_id, space_type, status);

CREATE TABLE IF NOT EXISTS tuso.bed (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    clinical_space_id   UUID NOT NULL REFERENCES tuso.clinical_space(id) ON DELETE CASCADE,
    code                VARCHAR(64) NOT NULL,
    bed_class           VARCHAR(24) NOT NULL,   -- WARD | RECOVERY | ICU | HDU | ISOLATION
    status              VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | OCCUPIED | CLOSED | MAINTENANCE
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_bed_class CHECK (bed_class IN ('WARD','RECOVERY','ICU','HDU','ISOLATION')),
    CONSTRAINT uq_bed_code UNIQUE (tenant_id, clinical_space_id, code)
);
CREATE INDEX IF NOT EXISTS idx_bed_space_class
    ON tuso.bed (tenant_id, bed_class, status);

CREATE TABLE IF NOT EXISTS tuso.equipment_asset (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    clinical_space_id   UUID REFERENCES tuso.clinical_space(id),
    name                VARCHAR(255) NOT NULL,
    equipment_type      VARCHAR(64),   -- ANAESTHETIC_MACHINE | C_ARM | DIATHERMY | MONITOR | ...
    maintenance_state   VARCHAR(24) NOT NULL DEFAULT 'OPERATIONAL',
        -- OPERATIONAL | DUE_SERVICE | OUT_OF_SERVICE
    status              VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | IN_USE | RESERVED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_equipment_maintenance
        CHECK (maintenance_state IN ('OPERATIONAL','DUE_SERVICE','OUT_OF_SERVICE')),
    CONSTRAINT uq_equipment_name UNIQUE (tenant_id, facility_id, name)
);
CREATE INDEX IF NOT EXISTS idx_equipment_facility_state
    ON tuso.equipment_asset (tenant_id, facility_id, maintenance_state, status);

CREATE TABLE IF NOT EXISTS tuso.instrument_set (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    name                VARCHAR(255) NOT NULL,
    sterilisation_state VARCHAR(24) NOT NULL DEFAULT 'STERILE',
        -- STERILE | IN_USE | REPROCESSING | EXPIRED
    sterile_until       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_instrument_state
        CHECK (sterilisation_state IN ('STERILE','IN_USE','REPROCESSING','EXPIRED')),
    CONSTRAINT uq_instrument_name UNIQUE (tenant_id, facility_id, name)
);
CREATE INDEX IF NOT EXISTS idx_instrument_facility_state
    ON tuso.instrument_set (tenant_id, facility_id, sterilisation_state);
