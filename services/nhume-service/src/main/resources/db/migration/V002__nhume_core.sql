-- ============================================================================
-- nhume-service V002 — Core delivery & fleet schema
-- ============================================================================

-- ---------------------------------------------------------------------------
-- nhume_delivery_requests — the canonical delivery request aggregate
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_requests (
    delivery_id              UUID            PRIMARY KEY,
    tenant_id                UUID            NOT NULL,
    pod_id                   VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    reference_code           VARCHAR(32)     NOT NULL,
    status                   VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    priority                 VARCHAR(16)     NOT NULL DEFAULT 'STANDARD',
    delivery_type            VARCHAR(48)     NOT NULL DEFAULT 'GENERAL',
    request_source           VARCHAR(48)     NOT NULL,
    requesting_actor_id      VARCHAR(255),
    requesting_actor_type    VARCHAR(48),
    requesting_org_id        VARCHAR(255),
    requesting_facility_id   VARCHAR(255),
    origin_kind              VARCHAR(32)     NOT NULL,
    origin_ref               VARCHAR(255)    NOT NULL,
    origin_label             VARCHAR(255),
    origin_address_line      VARCHAR(512),
    origin_lat               DOUBLE PRECISION,
    origin_lng               DOUBLE PRECISION,
    destination_kind         VARCHAR(32)     NOT NULL,
    destination_ref          VARCHAR(255)    NOT NULL,
    destination_label        VARCHAR(255),
    destination_address_line VARCHAR(512),
    destination_lat          DOUBLE PRECISION,
    destination_lng          DOUBLE PRECISION,
    recipient_kind           VARCHAR(32),
    recipient_ref            VARCHAR(255),
    recipient_name           VARCHAR(255),
    recipient_phone          VARCHAR(64),
    recipient_email          VARCHAR(255),
    recipient_language       VARCHAR(16),
    clinical_context_ref     VARCHAR(255),
    programme_context_ref    VARCHAR(255),
    telehealth_session_ref   VARCHAR(255),
    marketplace_order_ref    VARCHAR(255),
    payment_path             VARCHAR(48)     NOT NULL DEFAULT 'NONE',
    payment_reference        VARCHAR(255),
    declared_value_cents     BIGINT,
    currency                 VARCHAR(8),
    consent_required         BOOLEAN         NOT NULL DEFAULT FALSE,
    identity_verification    VARCHAR(32)     NOT NULL DEFAULT 'NONE',
    cold_chain_required      BOOLEAN         NOT NULL DEFAULT FALSE,
    controlled_item          BOOLEAN         NOT NULL DEFAULT FALSE,
    fragile                  BOOLEAN         NOT NULL DEFAULT FALSE,
    hazardous                BOOLEAN         NOT NULL DEFAULT FALSE,
    biohazard                BOOLEAN         NOT NULL DEFAULT FALSE,
    specimen                 BOOLEAN         NOT NULL DEFAULT FALSE,
    chain_of_custody_required BOOLEAN        NOT NULL DEFAULT FALSE,
    return_required          BOOLEAN         NOT NULL DEFAULT FALSE,
    required_by              TIMESTAMPTZ,
    pickup_window_start      TIMESTAMPTZ,
    pickup_window_end        TIMESTAMPTZ,
    delivery_window_start    TIMESTAMPTZ,
    delivery_window_end      TIMESTAMPTZ,
    allowed_modes_json       JSONB           NOT NULL DEFAULT '[]'::jsonb,
    policy_id                VARCHAR(64),
    sla_id                   VARCHAR(64),
    sla_due_at               TIMESTAMPTZ,
    notes                    TEXT,
    metadata_json            JSONB           NOT NULL DEFAULT '{}'::jsonb,
    correlation_id           UUID,
    is_demo                  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_by               VARCHAR(255),
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    submitted_at             TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    rejected_at              TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    delivered_at             TIMESTAMPTZ,
    failed_at                TIMESTAMPTZ,
    closed_at                TIMESTAMPTZ,
    CONSTRAINT uq_nhume_delivery_reference UNIQUE (tenant_id, reference_code)
);

CREATE INDEX idx_nhume_delivery_tenant      ON nhume_delivery_requests (tenant_id);
CREATE INDEX idx_nhume_delivery_status      ON nhume_delivery_requests (status);
CREATE INDEX idx_nhume_delivery_facility    ON nhume_delivery_requests (requesting_facility_id);
CREATE INDEX idx_nhume_delivery_type        ON nhume_delivery_requests (delivery_type);
CREATE INDEX idx_nhume_delivery_priority    ON nhume_delivery_requests (priority);
CREATE INDEX idx_nhume_delivery_created     ON nhume_delivery_requests (created_at DESC);
CREATE INDEX idx_nhume_delivery_sla_due     ON nhume_delivery_requests (sla_due_at) WHERE sla_due_at IS NOT NULL;
CREATE INDEX idx_nhume_delivery_telehealth  ON nhume_delivery_requests (telehealth_session_ref) WHERE telehealth_session_ref IS NOT NULL;
CREATE INDEX idx_nhume_delivery_marketplace ON nhume_delivery_requests (marketplace_order_ref)  WHERE marketplace_order_ref  IS NOT NULL;

-- ---------------------------------------------------------------------------
-- nhume_delivery_items — packaged items inside a delivery
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_items (
    item_id           UUID            PRIMARY KEY,
    delivery_id       UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    sequence_no       INT             NOT NULL DEFAULT 1,
    item_kind         VARCHAR(32)     NOT NULL,
    item_ref          VARCHAR(255),
    description       VARCHAR(512)    NOT NULL,
    quantity          NUMERIC(18,3)   NOT NULL DEFAULT 1,
    unit_of_measure   VARCHAR(32),
    package_type      VARCHAR(64),
    weight_grams      NUMERIC(18,3),
    volume_ml         NUMERIC(18,3),
    cold_chain        BOOLEAN         NOT NULL DEFAULT FALSE,
    controlled        BOOLEAN         NOT NULL DEFAULT FALSE,
    metadata_json     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_items_delivery ON nhume_delivery_items (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_delivery_packages — physical packages (parents of items)
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_packages (
    package_id        UUID            PRIMARY KEY,
    delivery_id       UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    package_code      VARCHAR(64)     NOT NULL,
    package_type      VARCHAR(64),
    seal_id           VARCHAR(128),
    weight_grams      NUMERIC(18,3),
    cold_chain        BOOLEAN         NOT NULL DEFAULT FALSE,
    fragile           BOOLEAN         NOT NULL DEFAULT FALSE,
    metadata_json     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_packages_delivery ON nhume_delivery_packages (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_dispatch_zones — zones used for dispatch / responsibility / coverage
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_dispatch_zones (
    zone_id           UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    code              VARCHAR(64)     NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    zone_kind         VARCHAR(32)     NOT NULL DEFAULT 'OPERATIONAL',
    jurisdiction_ref  VARCHAR(255),
    geometry_kind     VARCHAR(32)     NOT NULL DEFAULT 'CIRCLE',
    centre_lat        DOUBLE PRECISION,
    centre_lng        DOUBLE PRECISION,
    radius_m          INT,
    polygon_json      JSONB,
    notes             TEXT,
    active            BOOLEAN         NOT NULL DEFAULT TRUE,
    is_demo           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_nhume_zone_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_nhume_zones_tenant ON nhume_dispatch_zones (tenant_id);

-- ---------------------------------------------------------------------------
-- nhume_fleet_assets — vehicles, drones, robots, smart-lockers, etc.
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_fleet_assets (
    asset_id              UUID            PRIMARY KEY,
    tenant_id             UUID            NOT NULL,
    asset_code            VARCHAR(64)     NOT NULL,
    display_name          VARCHAR(255)    NOT NULL,
    registration_number   VARCHAR(64),
    vehicle_type          VARCHAR(64)     NOT NULL,
    mode_category         VARCHAR(48)     NOT NULL,
    ownership             VARCHAR(48)     NOT NULL DEFAULT 'OWNED',
    organisation_owner    VARCHAR(255),
    home_facility_ref     VARCHAR(255),
    assigned_programme    VARCHAR(255),
    assigned_zone_id      UUID            REFERENCES nhume_dispatch_zones(zone_id) ON DELETE SET NULL,
    assigned_courier_id   UUID,
    capacity_units        INT,
    weight_limit_kg       NUMERIC(10,2),
    volume_limit_l        NUMERIC(10,2),
    cold_chain_capable    BOOLEAN         NOT NULL DEFAULT FALSE,
    temperature_monitoring BOOLEAN        NOT NULL DEFAULT FALSE,
    specimen_capable      BOOLEAN         NOT NULL DEFAULT FALSE,
    hazmat_capable        BOOLEAN         NOT NULL DEFAULT FALSE,
    gps_capable           BOOLEAN         NOT NULL DEFAULT TRUE,
    telematics_provider   VARCHAR(64),
    insurance_ref         VARCHAR(128),
    licence_ref           VARCHAR(128),
    maintenance_status    VARCHAR(32)     NOT NULL DEFAULT 'OK',
    availability_status   VARCHAR(32)     NOT NULL DEFAULT 'AVAILABLE',
    operational_zone      VARCHAR(255),
    last_lat              DOUBLE PRECISION,
    last_lng              DOUBLE PRECISION,
    last_seen_at          TIMESTAMPTZ,
    compliance_docs_json  JSONB           NOT NULL DEFAULT '[]'::jsonb,
    autonomous_profile_json JSONB         NOT NULL DEFAULT '{}'::jsonb,
    active                BOOLEAN         NOT NULL DEFAULT TRUE,
    is_demo               BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_nhume_asset_code UNIQUE (tenant_id, asset_code)
);
CREATE INDEX idx_nhume_assets_tenant     ON nhume_fleet_assets (tenant_id);
CREATE INDEX idx_nhume_assets_mode       ON nhume_fleet_assets (mode_category);
CREATE INDEX idx_nhume_assets_avail      ON nhume_fleet_assets (availability_status);
CREATE INDEX idx_nhume_assets_facility   ON nhume_fleet_assets (home_facility_ref);

-- ---------------------------------------------------------------------------
-- nhume_vehicle_telemetry — telemetry / location stream
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_vehicle_telemetry (
    id               BIGSERIAL       PRIMARY KEY,
    asset_id         UUID            NOT NULL REFERENCES nhume_fleet_assets(asset_id) ON DELETE CASCADE,
    delivery_id      UUID            REFERENCES nhume_delivery_requests(delivery_id) ON DELETE SET NULL,
    captured_at      TIMESTAMPTZ     NOT NULL,
    lat              DOUBLE PRECISION,
    lng              DOUBLE PRECISION,
    speed_kph        NUMERIC(8,2),
    heading_deg      NUMERIC(6,2),
    altitude_m       NUMERIC(8,2),
    battery_pct      INT,
    temperature_c    NUMERIC(6,2),
    payload_locked   BOOLEAN,
    geofence_state   VARCHAR(32),
    source           VARCHAR(48)     NOT NULL DEFAULT 'NHUME',
    raw_json         JSONB,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_telem_asset_time ON nhume_vehicle_telemetry (asset_id, captured_at DESC);
CREATE INDEX idx_nhume_telem_delivery   ON nhume_vehicle_telemetry (delivery_id) WHERE delivery_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- nhume_driver_courier_profiles — drivers, couriers, CHWs, runners, robot ops
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_driver_courier_profiles (
    courier_id              UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    courier_code            VARCHAR(64)     NOT NULL,
    display_name            VARCHAR(255)    NOT NULL,
    courier_kind            VARCHAR(48)     NOT NULL DEFAULT 'COURIER',
    vito_person_ref         VARCHAR(255),
    varapi_provider_ref     VARCHAR(255),
    tuso_facility_ref       VARCHAR(255),
    indawo_site_ref         VARCHAR(255),
    keycloak_subject_id     VARCHAR(255),
    organisation_ref        VARCHAR(255),
    programme_ref           VARCHAR(255),
    phone                   VARCHAR(64),
    email                   VARCHAR(255),
    licence_ref             VARCHAR(128),
    licence_expiry          DATE,
    training_status         VARCHAR(32)     NOT NULL DEFAULT 'OK',
    delivery_zones_json     JSONB           NOT NULL DEFAULT '[]'::jsonb,
    allowed_modes_json      JSONB           NOT NULL DEFAULT '[]'::jsonb,
    device_fingerprint      VARCHAR(255),
    current_status          VARCHAR(32)     NOT NULL DEFAULT 'OFF_DUTY',
    risk_level              VARCHAR(16)     NOT NULL DEFAULT 'LOW',
    trust_verified          BOOLEAN         NOT NULL DEFAULT FALSE,
    council_verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    offline_access_allowed  BOOLEAN         NOT NULL DEFAULT FALSE,
    account_expires_at      TIMESTAMPTZ,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    is_demo                 BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_nhume_courier_code UNIQUE (tenant_id, courier_code)
);
CREATE INDEX idx_nhume_courier_tenant ON nhume_driver_courier_profiles (tenant_id);
CREATE INDEX idx_nhume_courier_kind   ON nhume_driver_courier_profiles (courier_kind);
CREATE INDEX idx_nhume_courier_status ON nhume_driver_courier_profiles (current_status);

-- ---------------------------------------------------------------------------
-- nhume_delivery_assignments — courier/asset assignment per delivery
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_assignments (
    assignment_id        UUID            PRIMARY KEY,
    delivery_id          UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    tenant_id            UUID            NOT NULL,
    courier_id           UUID            REFERENCES nhume_driver_courier_profiles(courier_id) ON DELETE SET NULL,
    asset_id             UUID            REFERENCES nhume_fleet_assets(asset_id) ON DELETE SET NULL,
    integration_provider VARCHAR(128),
    integration_ref      VARCHAR(255),
    mode_category        VARCHAR(48)     NOT NULL,
    assignment_kind      VARCHAR(32)     NOT NULL DEFAULT 'MANUAL',
    assigned_by          VARCHAR(255),
    assigned_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    accepted_at          TIMESTAMPTZ,
    declined_at          TIMESTAMPTZ,
    decline_reason       VARCHAR(512),
    superseded_at        TIMESTAMPTZ,
    superseded_by        UUID,
    status               VARCHAR(32)     NOT NULL DEFAULT 'ASSIGNED',
    notes                TEXT
);
CREATE INDEX idx_nhume_assign_delivery ON nhume_delivery_assignments (delivery_id);
CREATE INDEX idx_nhume_assign_courier  ON nhume_delivery_assignments (courier_id);
CREATE INDEX idx_nhume_assign_asset    ON nhume_delivery_assignments (asset_id);
CREATE INDEX idx_nhume_assign_active   ON nhume_delivery_assignments (delivery_id) WHERE superseded_at IS NULL;

-- ---------------------------------------------------------------------------
-- nhume_delivery_routes / nhume_delivery_stops — planned route + stops
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_routes (
    route_id          UUID            PRIMARY KEY,
    delivery_id       UUID            REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    assignment_id     UUID            REFERENCES nhume_delivery_assignments(assignment_id) ON DELETE CASCADE,
    tenant_id         UUID            NOT NULL,
    polyline_encoded  TEXT,
    distance_m        INT,
    duration_s        INT,
    eta_at            TIMESTAMPTZ,
    map_provider      VARCHAR(48),
    raw_json          JSONB,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_routes_delivery ON nhume_delivery_routes (delivery_id);

CREATE TABLE nhume_delivery_stops (
    stop_id           UUID            PRIMARY KEY,
    route_id          UUID            NOT NULL REFERENCES nhume_delivery_routes(route_id) ON DELETE CASCADE,
    sequence_no       INT             NOT NULL,
    stop_kind         VARCHAR(32)     NOT NULL,
    stop_ref          VARCHAR(255),
    label             VARCHAR(255),
    lat               DOUBLE PRECISION,
    lng               DOUBLE PRECISION,
    eta_at            TIMESTAMPTZ,
    arrived_at        TIMESTAMPTZ,
    departed_at       TIMESTAMPTZ,
    status            VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX idx_nhume_stops_route ON nhume_delivery_stops (route_id, sequence_no);

-- ---------------------------------------------------------------------------
-- nhume_delivery_status_events — append-only delivery status history
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_status_events (
    id               BIGSERIAL       PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    previous_status  VARCHAR(32),
    new_status       VARCHAR(32)     NOT NULL,
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(48),
    reason           VARCHAR(512),
    metadata_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_status_events_delivery ON nhume_delivery_status_events (delivery_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- nhume_delivery_tracking_events — fine-grained tracking points per delivery
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_tracking_events (
    id               BIGSERIAL       PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    captured_at      TIMESTAMPTZ     NOT NULL,
    event_kind       VARCHAR(48)     NOT NULL,
    lat              DOUBLE PRECISION,
    lng              DOUBLE PRECISION,
    accuracy_m       NUMERIC(8,2),
    courier_id       UUID,
    asset_id         UUID,
    source           VARCHAR(48)     NOT NULL DEFAULT 'NHUME',
    payload_json     JSONB,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_track_delivery ON nhume_delivery_tracking_events (delivery_id, captured_at DESC);
CREATE INDEX idx_nhume_track_kind     ON nhume_delivery_tracking_events (event_kind);

-- ---------------------------------------------------------------------------
-- nhume_delivery_proofs — captured proof of delivery / pickup
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_proofs (
    proof_id         UUID            PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    proof_stage      VARCHAR(32)     NOT NULL DEFAULT 'DELIVERY',
    proof_method     VARCHAR(32)     NOT NULL,
    captured_by      VARCHAR(255),
    captured_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    otp_code         VARCHAR(16),
    signature_uri    VARCHAR(1024),
    photo_uri        VARCHAR(1024),
    biometric_ref    VARCHAR(255),
    geofence_match   BOOLEAN,
    locker_code      VARCHAR(64),
    webhook_ref      VARCHAR(255),
    metadata_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    verified         BOOLEAN         NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_nhume_proof_delivery ON nhume_delivery_proofs (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_chain_of_custody_events — custody trail for sensitive deliveries
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_chain_of_custody_events (
    coc_id           UUID            PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    package_id       UUID            REFERENCES nhume_delivery_packages(package_id) ON DELETE SET NULL,
    sequence_no      INT             NOT NULL,
    event_kind       VARCHAR(48)     NOT NULL,
    custody_holder   VARCHAR(255),
    custody_location VARCHAR(255),
    seal_id          VARCHAR(128),
    temperature_c    NUMERIC(6,2),
    handover_notes   TEXT,
    verification     VARCHAR(48),
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(48),
    device_used      VARCHAR(255),
    evidence_uri     VARCHAR(1024),
    exception_flags  JSONB           NOT NULL DEFAULT '[]'::jsonb,
    audit_event_id   UUID,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_coc_delivery ON nhume_chain_of_custody_events (delivery_id, sequence_no);

-- ---------------------------------------------------------------------------
-- nhume_delivery_policies — delivery-type policy configuration
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_policies (
    policy_id              VARCHAR(64)    PRIMARY KEY,
    tenant_id              UUID           NOT NULL,
    delivery_type          VARCHAR(48)    NOT NULL,
    display_name           VARCHAR(255)   NOT NULL,
    description            TEXT,
    requires_approval      BOOLEAN        NOT NULL DEFAULT FALSE,
    requires_payment       BOOLEAN        NOT NULL DEFAULT FALSE,
    requires_stock_check   BOOLEAN        NOT NULL DEFAULT FALSE,
    requires_identity_verify VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    requires_consent       BOOLEAN        NOT NULL DEFAULT FALSE,
    requires_chain_of_custody BOOLEAN     NOT NULL DEFAULT FALSE,
    cold_chain_required    BOOLEAN        NOT NULL DEFAULT FALSE,
    proof_methods_required JSONB          NOT NULL DEFAULT '["RECIPIENT_SIGNATURE"]'::jsonb,
    allowed_modes          JSONB          NOT NULL DEFAULT '[]'::jsonb,
    allowed_courier_kinds  JSONB          NOT NULL DEFAULT '[]'::jsonb,
    notification_set       VARCHAR(64),
    sla_target_minutes     INT,
    escalation_rule        VARCHAR(64),
    active                 BOOLEAN        NOT NULL DEFAULT TRUE,
    is_demo                BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_policy_type ON nhume_delivery_policies (delivery_type);

-- ---------------------------------------------------------------------------
-- nhume_delivery_slas — SLA target definitions
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_slas (
    sla_id           VARCHAR(64)     PRIMARY KEY,
    tenant_id        UUID            NOT NULL,
    name             VARCHAR(255)    NOT NULL,
    priority         VARCHAR(16)     NOT NULL DEFAULT 'STANDARD',
    pickup_minutes   INT,
    delivery_minutes INT,
    description      TEXT,
    is_demo          BOOLEAN         NOT NULL DEFAULT FALSE
);

-- ---------------------------------------------------------------------------
-- nhume_delivery_exceptions — recorded issues / breaches
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_exceptions (
    exception_id     UUID            PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    exception_kind   VARCHAR(48)     NOT NULL,
    severity         VARCHAR(16)     NOT NULL DEFAULT 'WARNING',
    summary          VARCHAR(512)    NOT NULL,
    details_json     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    raised_by        VARCHAR(255),
    raised_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ,
    resolved_by      VARCHAR(255),
    resolution_notes TEXT
);
CREATE INDEX idx_nhume_excep_delivery ON nhume_delivery_exceptions (delivery_id);
CREATE INDEX idx_nhume_excep_kind     ON nhume_delivery_exceptions (exception_kind);

-- ---------------------------------------------------------------------------
-- nhume_delivery_notification_events — Comms-Hub-facing notification ledger
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_notification_events (
    id              BIGSERIAL       PRIMARY KEY,
    delivery_id     UUID            REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    event_code      VARCHAR(64)     NOT NULL,
    channel         VARCHAR(32)     NOT NULL,
    recipient_ref   VARCHAR(255),
    template_code   VARCHAR(64),
    payload_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    dispatched_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    delivery_state  VARCHAR(32)     NOT NULL DEFAULT 'QUEUED',
    provider_ref    VARCHAR(255)
);
CREATE INDEX idx_nhume_notif_delivery ON nhume_delivery_notification_events (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_delivery_integration_providers — registered third-party providers
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_integration_providers (
    provider_code     VARCHAR(64)    PRIMARY KEY,
    tenant_id         UUID           NOT NULL,
    display_name      VARCHAR(255)   NOT NULL,
    provider_kind     VARCHAR(48)    NOT NULL,
    base_url          VARCHAR(512),
    auth_kind         VARCHAR(32)    NOT NULL DEFAULT 'NONE',
    auth_ref          VARCHAR(255),
    webhook_secret    VARCHAR(255),
    config_json       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    simulation_mode   BOOLEAN        NOT NULL DEFAULT TRUE,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    is_demo           BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- nhume_autonomous_missions — drone / UAV / AGV / robot / locker missions
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_autonomous_missions (
    mission_id        UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    delivery_id       UUID            REFERENCES nhume_delivery_requests(delivery_id) ON DELETE SET NULL,
    asset_id          UUID            REFERENCES nhume_fleet_assets(asset_id) ON DELETE SET NULL,
    provider_code     VARCHAR(64)     REFERENCES nhume_delivery_integration_providers(provider_code) ON DELETE SET NULL,
    mission_kind      VARCHAR(48)     NOT NULL DEFAULT 'DRONE',
    status            VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    launch_site       VARCHAR(255),
    landing_site      VARCHAR(255),
    scheduled_for     TIMESTAMPTZ,
    launched_at       TIMESTAMPTZ,
    arrived_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    simulation_mode   BOOLEAN         NOT NULL DEFAULT TRUE,
    telemetry_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    raw_json          JSONB,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_mission_status   ON nhume_autonomous_missions (status);
CREATE INDEX idx_nhume_mission_delivery ON nhume_autonomous_missions (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_delivery_webhook_events — inbound webhook payloads (for traceability)
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_webhook_events (
    id               BIGSERIAL       PRIMARY KEY,
    provider_code    VARCHAR(64),
    delivery_id      UUID,
    mission_id       UUID,
    event_kind       VARCHAR(64),
    signature_valid  BOOLEAN         NOT NULL DEFAULT FALSE,
    received_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    payload_json     JSONB           NOT NULL,
    processed        BOOLEAN         NOT NULL DEFAULT FALSE,
    processing_error TEXT
);
CREATE INDEX idx_nhume_webhook_provider ON nhume_delivery_webhook_events (provider_code);
CREATE INDEX idx_nhume_webhook_delivery ON nhume_delivery_webhook_events (delivery_id);

-- ---------------------------------------------------------------------------
-- nhume_delivery_costing_records / nhume_delivery_payment_references
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_costing_records (
    id               BIGSERIAL       PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    component_code   VARCHAR(64)     NOT NULL,
    amount_cents     BIGINT          NOT NULL DEFAULT 0,
    currency         VARCHAR(8)      NOT NULL DEFAULT 'USD',
    metadata_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_costing_delivery ON nhume_delivery_costing_records (delivery_id);

CREATE TABLE nhume_delivery_payment_references (
    id               BIGSERIAL       PRIMARY KEY,
    delivery_id      UUID            NOT NULL REFERENCES nhume_delivery_requests(delivery_id) ON DELETE CASCADE,
    mushex_ref       VARCHAR(255),
    payment_path     VARCHAR(48)     NOT NULL,
    status           VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    amount_cents     BIGINT,
    currency         VARCHAR(8),
    notes            TEXT,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- nhume_delivery_audit_events — Nhume-specific audit trail (in addition to outbox)
-- ---------------------------------------------------------------------------
CREATE TABLE nhume_delivery_audit_events (
    id               BIGSERIAL       PRIMARY KEY,
    delivery_id      UUID,
    aggregate_type   VARCHAR(64)     NOT NULL,
    aggregate_id     VARCHAR(255)    NOT NULL,
    action           VARCHAR(64)     NOT NULL,
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(48),
    purpose_of_use   VARCHAR(48),
    correlation_id   UUID,
    facility_id      VARCHAR(255),
    workspace_id     VARCHAR(255),
    shift_id         VARCHAR(255),
    device_fingerprint VARCHAR(255),
    metadata_json    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_nhume_audit_delivery  ON nhume_delivery_audit_events (delivery_id);
CREATE INDEX idx_nhume_audit_aggregate ON nhume_delivery_audit_events (aggregate_type, aggregate_id);
CREATE INDEX idx_nhume_audit_action    ON nhume_delivery_audit_events (action);
