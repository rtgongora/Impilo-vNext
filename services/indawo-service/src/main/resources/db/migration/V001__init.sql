-- ============================================================================
-- indawo-service V001 — Initial schema
-- Provides: event_outbox (v1.1 envelope), idempotency_keys, location entities
-- ============================================================================

-- v1.1 Event Outbox (canonical schema per EVENTING_AND_TOPICS.md §2.2)
CREATE TABLE ind_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'indawo-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_ind_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ind_outbox_unpublished
    ON ind_event_outbox (created_at)
    WHERE published_at IS NULL;

-- Idempotency keys (keyed by tenant_id + pod_id + idempotency_key per tech-companion spec)
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ind_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- Addresses — standardized address records
CREATE TABLE ind_addresses (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    address_type    VARCHAR(32)     NOT NULL, -- PHYSICAL, POSTAL, SERVICE_POINT
    line1           VARCHAR(255)    NOT NULL,
    line2           VARCHAR(255),
    suburb          VARCHAR(128),
    city            VARCHAR(128),
    district        VARCHAR(128),
    province        VARCHAR(128)    NOT NULL,
    country         VARCHAR(3)      NOT NULL DEFAULT 'ZWE', -- ISO 3166-1 alpha-3
    postal_code     VARCHAR(16),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    geocode_quality VARCHAR(16),    -- HIGH, MEDIUM, LOW, MANUAL
    verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    entity_version  INT             NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ind_addresses_tenant ON ind_addresses (tenant_id);
CREATE INDEX idx_ind_addresses_province ON ind_addresses (province);
CREATE INDEX idx_ind_addresses_district ON ind_addresses (district) WHERE district IS NOT NULL;

-- Catchment Areas — defines geographic service areas
CREATE TABLE ind_catchment_areas (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    name            VARCHAR(255)    NOT NULL,
    area_type       VARCHAR(32)     NOT NULL, -- WARD, CONSTITUENCY, DISTRICT, PROVINCE
    parent_id       UUID            REFERENCES ind_catchment_areas(id),
    boundary_json   JSONB,          -- GeoJSON polygon/multipolygon
    population      INT,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ind_catchment_tenant ON ind_catchment_areas (tenant_id);
CREATE INDEX idx_ind_catchment_parent ON ind_catchment_areas (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_ind_catchment_type ON ind_catchment_areas (area_type);

-- Facility-Location Mappings — links facilities to addresses and catchment areas
CREATE TABLE ind_facility_locations (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    facility_id     VARCHAR(255)    NOT NULL, -- TUSO facility_id
    address_id      UUID            NOT NULL REFERENCES ind_addresses(id),
    catchment_id    UUID            REFERENCES ind_catchment_areas(id),
    primary_location BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ind_facility_address UNIQUE (facility_id, address_id)
);

CREATE INDEX idx_ind_fac_loc_tenant ON ind_facility_locations (tenant_id);
CREATE INDEX idx_ind_fac_loc_facility ON ind_facility_locations (facility_id);
CREATE INDEX idx_ind_fac_loc_catchment ON ind_facility_locations (catchment_id) WHERE catchment_id IS NOT NULL;
