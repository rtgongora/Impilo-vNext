-- =============================================================================
-- IoT V003 — Device Registry (Health OS §17)
--
-- "Devices are first-class entities in the Health Operating System and may
--  participate directly in the experience layer."
--
-- Device identity, trust levels, person linkage, capability, lifecycle.
-- =============================================================================

CREATE TABLE iot.device_registry (
    device_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    external_device_id      VARCHAR(255)    NOT NULL,
    device_type             VARCHAR(64)     NOT NULL,
    manufacturer            VARCHAR(255),
    model                   VARCHAR(255),
    firmware_version        VARCHAR(128),
    trust_level             VARCHAR(32)     NOT NULL DEFAULT 'UNVERIFIED',
    owner_health_id         VARCHAR(255),
    linked_equipment_id     UUID,
    capabilities            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    registered_by           VARCHAR(255)    NOT NULL,
    registered_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_seen_at            TIMESTAMPTZ,
    revoked_at              TIMESTAMPTZ,
    revoked_by              VARCHAR(255),
    revocation_reason       TEXT,
    metadata_json           JSONB,

    CONSTRAINT uq_device_tenant_ext UNIQUE (tenant_id, external_device_id)
);

CREATE INDEX idx_device_tenant ON iot.device_registry (tenant_id);
CREATE INDEX idx_device_owner ON iot.device_registry (owner_health_id) WHERE owner_health_id IS NOT NULL;
CREATE INDEX idx_device_trust ON iot.device_registry (trust_level);
CREATE INDEX idx_device_status ON iot.device_registry (status);
CREATE INDEX idx_device_type ON iot.device_registry (device_type);
