-- ============================================================================
-- asset-registry-service V002 — Asset table
-- Provides: asr_assets (lifecycle-tracked equipment/devices/vehicles)
-- ============================================================================

CREATE TABLE asr_assets (
    asset_id        UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_ref    VARCHAR(255)    NOT NULL,
    type            VARCHAR(64)     NOT NULL,
    serial_no       VARCHAR(255),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    metadata_json   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version         INT             NOT NULL DEFAULT 1
);

CREATE INDEX idx_asr_assets_tenant_id ON asr_assets (tenant_id);
CREATE INDEX idx_asr_assets_facility_ref ON asr_assets (facility_ref);
CREATE INDEX idx_asr_assets_status ON asr_assets (status);
CREATE INDEX idx_asr_assets_updated_at ON asr_assets (updated_at);
