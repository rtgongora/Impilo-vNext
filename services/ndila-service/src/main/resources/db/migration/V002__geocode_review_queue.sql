-- Geocoding review queue for facilities imported without valid coordinates.

CREATE TABLE IF NOT EXISTS ndila_geocode_review_queue (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    owner_service       VARCHAR(32) NOT NULL DEFAULT 'TUSO',
    owner_entity_type   VARCHAR(32) NOT NULL DEFAULT 'FACILITY',
    owner_entity_id     VARCHAR(64) NOT NULL,
    facility_uid        VARCHAR(64),
    facility_code       VARCHAR(64),
    facility_name       VARCHAR(255) NOT NULL,
    province            VARCHAR(100),
    district            VARCHAR(100),
    reason              VARCHAR(64) NOT NULL DEFAULT 'MISSING_COORDINATES',
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT uq_ndila_geocode_review_owner UNIQUE (tenant_id, owner_service, owner_entity_id)
);

CREATE INDEX IF NOT EXISTS idx_ndila_geocode_review_status
    ON ndila_geocode_review_queue (tenant_id, status);
