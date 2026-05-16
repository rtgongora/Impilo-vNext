-- ============================================================================
-- ndila-service V001 — Initial schema
--
-- Ndila is the spatial intelligence layer of Impilo vNext. This migration
-- creates the canonical Ndila tables for locations, boundaries, geofences,
-- catchments, tracking assets, tracking events, provider configs, intelligence
-- signals, AI context packets, recommendations, offline queue and the
-- canonical outbox.
--
-- PostGIS strategy:
--   - This migration attempts to enable the PostGIS extension. If PostGIS is
--     not available on the target cluster, the migration continues and only
--     stores GeoJSON in jsonb columns plus plain numeric latitude/longitude.
--   - All spatial geometry columns and spatial indexes are guarded by
--     "DO $$ BEGIN ... EXCEPTION WHEN ... END $$" blocks so failures degrade
--     gracefully.
--   - Production clusters should switch the Postgres image to
--     postgis/postgis:16-3.4 and run this migration once. Subsequent
--     application restarts pick up the geometry columns automatically.
-- ============================================================================

-- Enable PostGIS if available (graceful no-op otherwise).
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS postgis;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'PostGIS not available — Ndila will run in JSONB-only mode (production should install postgis/postgis:16-3.4).';
    END;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- Outbox + idempotency (canonical Impilo pattern)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'ndila-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_ndila_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ndila_outbox_unpublished
    ON ndila_event_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ndila_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Locations (canonical)
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_locations (
    id                          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    pod_id                      VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    owner_service               VARCHAR(64)     NOT NULL,           -- TUSO, INDAWO, NHUME, VARAPI, VITO, ...
    owner_entity_type           VARCHAR(64)     NOT NULL,           -- FACILITY, PUBLIC_HEALTH_SITE, PROVIDER_PRACTICE, ...
    owner_entity_id             VARCHAR(255),
    name                        VARCHAR(255)    NOT NULL,
    description                 TEXT,
    location_type               VARCHAR(64)     NOT NULL,           -- HEALTH_FACILITY, PHARMACY, LAB, MARKET, WATER_POINT, ...
    latitude                    NUMERIC(10,7),
    longitude                   NUMERIC(10,7),
    altitude_meters             NUMERIC(8,2),
    address_line1               VARCHAR(255),
    address_line2               VARCHAR(255),
    locality                    VARCHAR(128),
    ward                        VARCHAR(128),
    district                    VARCHAR(128),
    province                    VARCHAR(128),
    country                     VARCHAR(3)      NOT NULL DEFAULT 'ZWE',
    postal_code                 VARCHAR(16),
    plus_code                   VARCHAR(32),
    what3words                  VARCHAR(64),
    geohash                     VARCHAR(16),
    h3_index                    VARCHAR(32),
    coordinate_accuracy_meters  NUMERIC(8,2),
    source                      VARCHAR(32)     NOT NULL DEFAULT 'USER_ENTERED', -- USER_ENTERED|GPS|REGISTRY|IMPORT|GEOCODED|EXTERNAL_GIS|DEVICE_TELEMETRY|INSPECTION|DELIVERY|PUBLIC_HEALTH_OPERATION|NOMPILO_ASSISTED
    verification_status         VARCHAR(32)     NOT NULL DEFAULT 'UNVERIFIED',   -- UNVERIFIED|SELF_REPORTED|SYSTEM_VALIDATED|REGISTRY_VALIDATED|FIELD_VERIFIED|AUTHORITY_VERIFIED|REJECTED
    verified_by                 VARCHAR(255),
    verified_at                 TIMESTAMPTZ,
    boundary_id                 UUID,
    catchment_area_id           UUID,
    sensitivity_level           VARCHAR(32)     NOT NULL DEFAULT 'PUBLIC',       -- PUBLIC|INTERNAL|RESTRICTED|HIGHLY_RESTRICTED
    geojson                     JSONB,
    is_active                   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Optional PostGIS point column for locations.
DO $$
BEGIN
    BEGIN
        ALTER TABLE ndila_locations ADD COLUMN geom GEOGRAPHY(Point, 4326);
        CREATE INDEX idx_ndila_locations_geom ON ndila_locations USING GIST (geom);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Ndila locations geom column skipped (PostGIS unavailable).';
    END;
END $$;

CREATE INDEX idx_ndila_locations_tenant ON ndila_locations (tenant_id);
CREATE INDEX idx_ndila_locations_owner ON ndila_locations (owner_service, owner_entity_type, owner_entity_id);
CREATE INDEX idx_ndila_locations_type ON ndila_locations (location_type);
CREATE INDEX idx_ndila_locations_district ON ndila_locations (district) WHERE district IS NOT NULL;
CREATE INDEX idx_ndila_locations_sensitivity ON ndila_locations (sensitivity_level);
CREATE INDEX idx_ndila_locations_active ON ndila_locations (is_active);

CREATE TABLE ndila_location_aliases (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    location_id     UUID            NOT NULL REFERENCES ndila_locations(id) ON DELETE CASCADE,
    alias_type      VARCHAR(32)     NOT NULL, -- LEGACY_NAME, EXTERNAL_ID, LOCAL_NAME
    alias_value     VARCHAR(255)    NOT NULL,
    source          VARCHAR(64),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndila_location_aliases_loc ON ndila_location_aliases (location_id);
CREATE INDEX idx_ndila_location_aliases_val ON ndila_location_aliases (alias_value);

-- ─────────────────────────────────────────────────────────────────────────────
-- Boundaries
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_boundaries (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    boundary_type   VARCHAR(32)     NOT NULL, -- COUNTRY|PROVINCE|DISTRICT|WARD|CATCHMENT|CUSTOM
    parent_id       UUID            REFERENCES ndila_boundaries(id),
    external_code   VARCHAR(64),
    geometry_json   JSONB,
    population_estimate INT,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    BEGIN
        ALTER TABLE ndila_boundaries ADD COLUMN geom GEOGRAPHY(MultiPolygon, 4326);
        CREATE INDEX idx_ndila_boundaries_geom ON ndila_boundaries USING GIST (geom);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Ndila boundaries geom column skipped (PostGIS unavailable).';
    END;
END $$;

CREATE INDEX idx_ndila_boundaries_tenant ON ndila_boundaries (tenant_id);
CREATE INDEX idx_ndila_boundaries_type ON ndila_boundaries (boundary_type);

-- ─────────────────────────────────────────────────────────────────────────────
-- Geofences
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_geofences (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    geofence_type       VARCHAR(48)     NOT NULL, -- FACILITY_BOUNDARY|WARD_BOUNDARY|...|CUSTOM
    geometry_json       JSONB           NOT NULL,           -- GeoJSON
    center_latitude     NUMERIC(10,7),
    center_longitude    NUMERIC(10,7),
    radius_meters       NUMERIC(12,2),
    owner_service       VARCHAR(64),
    owner_entity_type   VARCHAR(64),
    owner_entity_id     VARCHAR(255),
    risk_level          VARCHAR(16)     NOT NULL DEFAULT 'LOW', -- LOW|MEDIUM|HIGH|CRITICAL
    rules_json          JSONB,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    BEGIN
        ALTER TABLE ndila_geofences ADD COLUMN geom GEOGRAPHY(Polygon, 4326);
        CREATE INDEX idx_ndila_geofences_geom ON ndila_geofences USING GIST (geom);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Ndila geofences geom column skipped (PostGIS unavailable).';
    END;
END $$;

CREATE INDEX idx_ndila_geofences_tenant ON ndila_geofences (tenant_id);
CREATE INDEX idx_ndila_geofences_type ON ndila_geofences (geofence_type);
CREATE INDEX idx_ndila_geofences_owner ON ndila_geofences (owner_service, owner_entity_id);
CREATE INDEX idx_ndila_geofences_active ON ndila_geofences (is_active);

-- ─────────────────────────────────────────────────────────────────────────────
-- Catchment areas
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_catchment_areas (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    owner_service       VARCHAR(64)     NOT NULL,
    owner_entity_type   VARCHAR(64),
    owner_entity_id     VARCHAR(255),
    geometry_type       VARCHAR(16)     NOT NULL DEFAULT 'POLYGON', -- RADIUS|POLYGON|MULTIPOLYGON|ISOCHRONE
    geometry_json       JSONB,
    center_latitude     NUMERIC(10,7),
    center_longitude    NUMERIC(10,7),
    radius_meters       NUMERIC(12,2),
    population_estimate INT,
    households_estimate INT,
    source              VARCHAR(32)     NOT NULL DEFAULT 'MANUAL',   -- MANUAL|IMPORT|DERIVED_RADIUS|DERIVED_ISOCHRONE|EXTERNAL_GIS
    verification_status VARCHAR(32)     NOT NULL DEFAULT 'UNVERIFIED',
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    BEGIN
        ALTER TABLE ndila_catchment_areas ADD COLUMN geom GEOGRAPHY(Polygon, 4326);
        CREATE INDEX idx_ndila_catchments_geom ON ndila_catchment_areas USING GIST (geom);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Ndila catchments geom column skipped (PostGIS unavailable).';
    END;
END $$;

CREATE INDEX idx_ndila_catchments_tenant ON ndila_catchment_areas (tenant_id);
CREATE INDEX idx_ndila_catchments_owner ON ndila_catchment_areas (owner_service, owner_entity_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Tracking
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_tracking_assets (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    asset_id        VARCHAR(255)    NOT NULL,
    asset_type      VARCHAR(48)     NOT NULL, -- VEHICLE|MOTORBIKE|AMBULANCE|DELIVERY_RIDER|MOBILE_OUTREACH_TEAM|FIELD_INSPECTOR|COURIER|DRONE|ROBOT|AUTONOMOUS_VEHICLE|COLD_CHAIN_CONTAINER|MEDICAL_SUPPLY_PACKAGE|LAB_SAMPLE|LAB_COURIER|EMERGENCY_RESPONSE_TEAM|OTHER
    owner_service   VARCHAR(64),
    owner_entity_id VARCHAR(255),
    label           VARCHAR(255),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    sensitivity_level VARCHAR(32)   NOT NULL DEFAULT 'INTERNAL',
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ndila_tracking_asset UNIQUE (tenant_id, asset_id)
);

CREATE INDEX idx_ndila_tracking_assets_owner ON ndila_tracking_assets (owner_service, owner_entity_id);
CREATE INDEX idx_ndila_tracking_assets_type ON ndila_tracking_assets (asset_type);

CREATE TABLE ndila_tracking_events (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    asset_id            VARCHAR(255)    NOT NULL,
    asset_type          VARCHAR(48)     NOT NULL,
    owner_service       VARCHAR(64),
    owner_entity_id     VARCHAR(255),
    latitude            NUMERIC(10,7)   NOT NULL,
    longitude           NUMERIC(10,7)   NOT NULL,
    altitude_meters     NUMERIC(8,2),
    speed_mps           NUMERIC(8,2),
    heading_degrees     NUMERIC(6,2),
    accuracy_meters     NUMERIC(8,2),
    battery_level       NUMERIC(5,2),
    network_type        VARCHAR(16),
    telemetry_source    VARCHAR(32)     NOT NULL DEFAULT 'MOBILE_APP',
    event_timestamp     TIMESTAMPTZ     NOT NULL,
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    metadata            JSONB
);

DO $$
BEGIN
    BEGIN
        ALTER TABLE ndila_tracking_events ADD COLUMN geom GEOGRAPHY(Point, 4326);
        CREATE INDEX idx_ndila_tracking_events_geom ON ndila_tracking_events USING GIST (geom);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Ndila tracking_events geom column skipped (PostGIS unavailable).';
    END;
END $$;

CREATE INDEX idx_ndila_tracking_events_asset ON ndila_tracking_events (asset_id);
CREATE INDEX idx_ndila_tracking_events_tenant ON ndila_tracking_events (tenant_id);
CREATE INDEX idx_ndila_tracking_events_time ON ndila_tracking_events (event_timestamp DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- Route cache
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_routes_cache (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    cache_key       VARCHAR(255)    NOT NULL,
    origin_lat      NUMERIC(10,7)   NOT NULL,
    origin_lng      NUMERIC(10,7)   NOT NULL,
    destination_lat NUMERIC(10,7)   NOT NULL,
    destination_lng NUMERIC(10,7)   NOT NULL,
    mode            VARCHAR(32)     NOT NULL,
    distance_meters NUMERIC(14,2),
    duration_seconds NUMERIC(12,2),
    provider        VARCHAR(64),
    payload_json    JSONB,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ndila_routes_cache_key UNIQUE (tenant_id, cache_key)
);

CREATE INDEX idx_ndila_routes_cache_exp ON ndila_routes_cache (expires_at) WHERE expires_at IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- Provider observability
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_provider_configs (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID,                         -- NULL = platform default
    provider_name       VARCHAR(64)     NOT NULL,
    operation_type      VARCHAR(48)     NOT NULL,     -- GEOCODING|REVERSE_GEOCODING|ROUTING|DISTANCE_MATRIX|TILES|BOUNDARIES|ISOCHRONE|SPATIAL_SEARCH|TRAFFIC|HEALTH
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    priority            INT             NOT NULL DEFAULT 100,
    base_url            VARCHAR(255),
    credentials_ref     VARCHAR(255),                  -- pointer (Keys service ref)
    capabilities_json   JSONB,
    data_residency_notes TEXT,
    privacy_notes       TEXT,
    estimated_cost_category VARCHAR(16),               -- FREE|LOW|MEDIUM|HIGH
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ndila_provider_cfg UNIQUE (tenant_id, provider_name, operation_type)
);

CREATE TABLE ndila_provider_policies (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID,
    operation_type      VARCHAR(48)     NOT NULL,
    use_case            VARCHAR(64),                   -- e.g. DISPATCH_DELIVERY, EMERGENCY_RESPONSE, CITIZEN_NEARBY
    sensitivity_level   VARCHAR(32),                   -- max sensitivity this policy applies to
    required_capabilities_json JSONB,
    preferred_chain_json JSONB,                        -- ordered provider names
    forbidden_providers_json JSONB,
    minimize_payload    BOOLEAN         NOT NULL DEFAULT TRUE,
    require_tshepo_authz BOOLEAN        NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndila_provider_policies_op ON ndila_provider_policies (operation_type);

CREATE TABLE ndila_provider_requests (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(255),
    actor_type          VARCHAR(64),
    purpose_of_use      VARCHAR(64),
    use_case            VARCHAR(64),
    sensitivity_level   VARCHAR(32),
    provider_name       VARCHAR(64)     NOT NULL,
    operation_type      VARCHAR(48)     NOT NULL,
    fallback_used       BOOLEAN         NOT NULL DEFAULT FALSE,
    served_from_cache   BOOLEAN         NOT NULL DEFAULT FALSE,
    confidence          NUMERIC(5,4),
    response_status     INT,
    latency_ms          INT,
    cost_units          NUMERIC(10,4),
    correlation_id      UUID,
    policy_decision_id  VARCHAR(64),
    pii_minimized       BOOLEAN         NOT NULL DEFAULT TRUE,
    request_summary     TEXT,
    response_summary    TEXT,
    requested_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndila_provider_requests_tenant ON ndila_provider_requests (tenant_id);
CREATE INDEX idx_ndila_provider_requests_provider ON ndila_provider_requests (provider_name);
CREATE INDEX idx_ndila_provider_requests_time ON ndila_provider_requests (requested_at DESC);

CREATE TABLE ndila_provider_health (
    provider_name       VARCHAR(64)     NOT NULL,
    operation_type      VARCHAR(48)     NOT NULL,
    healthy             BOOLEAN         NOT NULL,
    last_check_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_error          TEXT,
    consecutive_failures INT            NOT NULL DEFAULT 0,
    avg_latency_ms      INT,
    CONSTRAINT pk_ndila_provider_health PRIMARY KEY (provider_name, operation_type)
);

CREATE TABLE ndila_provider_usage (
    tenant_id           UUID            NOT NULL,
    provider_name       VARCHAR(64)     NOT NULL,
    operation_type      VARCHAR(48)     NOT NULL,
    usage_window        DATE            NOT NULL,
    request_count       BIGINT          NOT NULL DEFAULT 0,
    error_count         BIGINT          NOT NULL DEFAULT 0,
    cost_units          NUMERIC(12,4)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_ndila_provider_usage PRIMARY KEY (tenant_id, provider_name, operation_type, usage_window)
);

CREATE TABLE ndila_provider_cache_records (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    operation_type  VARCHAR(48)     NOT NULL,
    cache_key       VARCHAR(255)    NOT NULL,
    payload_json    JSONB,
    expires_at      TIMESTAMPTZ,
    hit_count       INT             NOT NULL DEFAULT 0,
    last_hit_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ndila_provider_cache_key UNIQUE (tenant_id, operation_type, cache_key)
);

CREATE TABLE ndila_tile_configs (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID,
    provider_name       VARCHAR(64)     NOT NULL,
    tile_url_template   VARCHAR(512),                   -- accessible to backend only; tokens not exposed to FE
    attribution         VARCHAR(255),
    max_zoom            INT             NOT NULL DEFAULT 18,
    supports_offline    BOOLEAN         NOT NULL DEFAULT FALSE,
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    production_safe     BOOLEAN         NOT NULL DEFAULT TRUE,    -- false for public OSM
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Spatial intelligence outputs
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_intelligence_signals (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    signal_type     VARCHAR(64)     NOT NULL, -- COORDINATE_QUALITY|SERVICE_GAP|RISK_CLUSTER|INSPECTION_GAP|ROUTE_DEVIATION|GEOFENCE_BREACH|...
    severity        VARCHAR(16)     NOT NULL DEFAULT 'LOW',
    area_level      VARCHAR(16),    -- WARD|DISTRICT|PROVINCE|NATIONAL
    area_id         VARCHAR(128),
    area_name       VARCHAR(255),
    summary         TEXT,
    payload_json    JSONB,
    confidence      NUMERIC(5,4),
    detected_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_ndila_signals_tenant ON ndila_intelligence_signals (tenant_id);
CREATE INDEX idx_ndila_signals_type ON ndila_intelligence_signals (signal_type);
CREATE INDEX idx_ndila_signals_area ON ndila_intelligence_signals (area_level, area_id);

CREATE TABLE ndila_ai_context_packets (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(255),
    actor_type          VARCHAR(64),
    purpose_of_use      VARCHAR(64),
    question            TEXT,
    summary             TEXT,
    signals_json        JSONB,
    recommendations_json JSONB,
    restrictions_json   JSONB,
    confidence          NUMERIC(5,4),
    area_scope_json     JSONB,
    produced_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndila_ai_packets_tenant ON ndila_ai_context_packets (tenant_id);

CREATE TABLE ndila_recommendations (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    recommendation_type VARCHAR(64) NOT NULL,
    target_service  VARCHAR(64),
    target_entity_id VARCHAR(255),
    summary         TEXT,
    payload_json    JSONB,
    priority        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(16)     NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Offline queue
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE ndila_offline_queue (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255),
    queue_type      VARCHAR(48)     NOT NULL, -- LOCATION_SUBMISSION|TRACKING_EVENT|VERIFICATION_REQUEST
    payload_json    JSONB           NOT NULL,
    sync_status     VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING|SYNCED|REJECTED
    captured_at     TIMESTAMPTZ     NOT NULL,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    synced_at       TIMESTAMPTZ,
    failure_reason  TEXT
);

CREATE INDEX idx_ndila_offline_queue_status ON ndila_offline_queue (sync_status);
CREATE INDEX idx_ndila_offline_queue_tenant ON ndila_offline_queue (tenant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed: refuse public OSM as production tile target.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO ndila_tile_configs
    (provider_name, tile_url_template, attribution, max_zoom, supports_offline, is_default, production_safe, notes)
VALUES
    ('MOCK', 'mock://tiles/{z}/{x}/{y}.png', 'Ndila Mock Tiles', 19, true, true, true,
     'Default mock tile config for dev/test/CI.'),
    ('PUBLIC_OSM_BLOCKED', 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
     'OpenStreetMap contributors', 19, false, false, false,
     'Reference entry only — Ndila refuses to select PUBLIC_OSM_BLOCKED as a production tile provider. Self-host OSM tiles or use a managed OSM-derived tile service.');
