-- =============================================================================
-- asset-registry-service V004 — Equipment entity (Health OS §9)
--
-- Doctrine: "Asset ID identifies a managed asset from an ownership, deployment,
-- finance, governance, or lifecycle perspective. Equipment ID identifies a
-- specific operational device or piece of equipment in use for care, diagnostics,
-- monitoring, support, or infrastructure."
--
-- Equipment is a first-class entity separate from the governance-tracked Asset.
-- Equipment may be linked to an Asset (for ownership tracking) but operates
-- independently in clinical and operational workflows.
-- =============================================================================

CREATE TABLE asr_equipment (
    equipment_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    facility_id         VARCHAR(255)    NOT NULL,
    asset_id            UUID            REFERENCES asr_assets(asset_id) ON DELETE SET NULL,
    name                VARCHAR(512)    NOT NULL,
    equipment_type      VARCHAR(64)     NOT NULL,
    model               VARCHAR(255),
    manufacturer        VARCHAR(255),
    serial_number       VARCHAR(255),
    status              VARCHAR(32)     NOT NULL DEFAULT 'OPERATIONAL',
    department_id       VARCHAR(255),
    ward_id             VARCHAR(255),
    location_description VARCHAR(512),
    last_calibration    DATE,
    next_calibration    DATE,
    last_maintenance    DATE,
    next_maintenance    DATE,
    device_id           VARCHAR(255),
    metadata_json       JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asr_equipment_tenant ON asr_equipment (tenant_id);
CREATE INDEX idx_asr_equipment_facility ON asr_equipment (facility_id);
CREATE INDEX idx_asr_equipment_asset ON asr_equipment (asset_id) WHERE asset_id IS NOT NULL;
CREATE INDEX idx_asr_equipment_type ON asr_equipment (equipment_type);
CREATE INDEX idx_asr_equipment_status ON asr_equipment (status);
CREATE INDEX idx_asr_equipment_device ON asr_equipment (device_id) WHERE device_id IS NOT NULL;
CREATE INDEX idx_asr_equipment_maintenance ON asr_equipment (next_maintenance) WHERE next_maintenance IS NOT NULL;
