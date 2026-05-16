-- ============================================================================
-- dispatch-service V003 — Delivery, fleet, courier, policy, and mission domain
-- ============================================================================

CREATE TABLE dsp_dispatch_zones (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    zone_code               VARCHAR(128)    NOT NULL,
    zone_name               VARCHAR(255)    NOT NULL,
    boundary_json           JSONB,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_dispatch_zone_code ON dsp_dispatch_zones (tenant_id, zone_code);

CREATE TABLE dsp_delivery_policies (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    policy_code             VARCHAR(128)    NOT NULL,
    policy_name             VARCHAR(255)    NOT NULL,
    rules_json              JSONB,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_delivery_policy_code ON dsp_delivery_policies (tenant_id, policy_code);

CREATE TABLE dsp_delivery_integrations (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    provider_code           VARCHAR(128)    NOT NULL,
    provider_name           VARCHAR(255)    NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    config_json             JSONB,
    last_webhook_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_delivery_provider_code ON dsp_delivery_integrations (tenant_id, provider_code);

CREATE TABLE dsp_fleet_assets (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    asset_code              VARCHAR(128)    NOT NULL,
    asset_type              VARCHAR(64)     NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'AVAILABLE',
    plate_number            VARCHAR(64),
    capacity_kg             NUMERIC(10,2),
    metadata_json           JSONB,
    last_latitude           DOUBLE PRECISION,
    last_longitude          DOUBLE PRECISION,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_fleet_asset_code ON dsp_fleet_assets (tenant_id, asset_code);

CREATE TABLE dsp_driver_courier_profiles (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    courier_code            VARCHAR(128)    NOT NULL,
    full_name               VARCHAR(255)    NOT NULL,
    phone                   VARCHAR(64),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    rating                  NUMERIC(4,2),
    metadata_json           JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_courier_code ON dsp_driver_courier_profiles (tenant_id, courier_code);

CREATE TABLE dsp_delivery_requests (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    external_ref            VARCHAR(255),
    requester_actor_id      VARCHAR(128),
    requester_actor_type    VARCHAR(64),
    pickup_location         VARCHAR(255)    NOT NULL,
    dropoff_location        VARCHAR(255)    NOT NULL,
    requested_window_start  TIMESTAMPTZ,
    requested_window_end    TIMESTAMPTZ,
    priority                VARCHAR(32)     NOT NULL DEFAULT 'NORMAL',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'REQUESTED',
    payload_json            JSONB,
    zone_id                 UUID REFERENCES dsp_dispatch_zones(id),
    policy_id               UUID REFERENCES dsp_delivery_policies(id),
    integration_provider_id UUID REFERENCES dsp_delivery_integrations(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_delivery_requests_tenant_status
    ON dsp_delivery_requests (tenant_id, status);

CREATE TABLE dsp_delivery_assignments (
    id                      UUID            PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    fleet_asset_id          UUID REFERENCES dsp_fleet_assets(id),
    courier_profile_id      UUID REFERENCES dsp_driver_courier_profiles(id),
    assignment_status       VARCHAR(32)     NOT NULL DEFAULT 'ASSIGNED',
    notes_json              JSONB,
    assigned_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    accepted_at             TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_delivery_assignments_delivery_id ON dsp_delivery_assignments (delivery_id);

CREATE TABLE dsp_delivery_tracking_events (
    id                      BIGSERIAL       PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    assignment_id           UUID REFERENCES dsp_delivery_assignments(id),
    event_type              VARCHAR(64)     NOT NULL,
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    accuracy_meters         DOUBLE PRECISION,
    source                  VARCHAR(64),
    details_json            JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_tracking_delivery_id ON dsp_delivery_tracking_events (delivery_id);

CREATE TABLE dsp_delivery_proofs (
    id                      UUID            PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    proof_type              VARCHAR(64)     NOT NULL,
    proof_ref               VARCHAR(255),
    otp_code                VARCHAR(64),
    captured_by             VARCHAR(128),
    details_json            JSONB,
    captured_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE dsp_chain_of_custody_events (
    id                      BIGSERIAL       PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    event_type              VARCHAR(64)     NOT NULL,
    actor_id                VARCHAR(128),
    actor_type              VARCHAR(64),
    notes_json              JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dsp_custody_delivery_id ON dsp_chain_of_custody_events (delivery_id);

CREATE TABLE dsp_autonomous_missions (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    delivery_id             UUID REFERENCES dsp_delivery_requests(id),
    mission_code            VARCHAR(128)    NOT NULL,
    vehicle_ref             VARCHAR(128),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PLANNED',
    mission_payload_json    JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_dsp_autonomous_mission_code ON dsp_autonomous_missions (tenant_id, mission_code);

CREATE TABLE dsp_delivery_audit_events (
    id                      BIGSERIAL       PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    delivery_id             UUID,
    event_code              VARCHAR(128)    NOT NULL,
    details_json            JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE dsp_delivery_notifications (
    id                      BIGSERIAL       PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    delivery_id             UUID REFERENCES dsp_delivery_requests(id),
    channel                 VARCHAR(64)     NOT NULL,
    recipient_ref           VARCHAR(255),
    payload_json            JSONB,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE dsp_delivery_costs (
    id                      BIGSERIAL       PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    amount                  NUMERIC(12,2)   NOT NULL,
    currency                VARCHAR(8)      NOT NULL DEFAULT 'USD',
    cost_type               VARCHAR(64)     NOT NULL,
    details_json            JSONB,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE dsp_delivery_payments (
    id                      BIGSERIAL       PRIMARY KEY,
    delivery_id             UUID            NOT NULL REFERENCES dsp_delivery_requests(id),
    payment_ref             VARCHAR(255),
    amount                  NUMERIC(12,2)   NOT NULL,
    currency                VARCHAR(8)      NOT NULL DEFAULT 'USD',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    paid_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
