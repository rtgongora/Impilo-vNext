-- =============================================================================
-- MSIKA-FLOW V002 — Carts + Fulfillment Orders + Multi-Modal Logistics
-- Adds persisted carts/checkout and a generalized delivery/custody/proof model.
-- =============================================================================

-- ── Carts ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mf_carts (
    cart_id             VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(128)    NOT NULL,
    actor_type          VARCHAR(20)     NOT NULL,
    patient_cpid        VARCHAR(64),
    channel             VARCHAR(30)     NOT NULL DEFAULT 'WEB',
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN', -- OPEN|CHECKED_OUT|ABANDONED
    metadata            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mf_cart_items (
    id                  VARCHAR(26)     PRIMARY KEY,
    cart_id             VARCHAR(26)     NOT NULL REFERENCES mf_carts(cart_id),
    msika_core_code     VARCHAR(128)    NOT NULL,
    kind                VARCHAR(10)     NOT NULL DEFAULT 'PRODUCT',
    qty                 INT             NOT NULL DEFAULT 1,
    fulfillment_mode    VARCHAR(30),
    metadata            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mf_carts_actor ON mf_carts (tenant_id, actor_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_mf_cart_items_cart ON mf_cart_items (cart_id);

-- ── Fulfillment Orders (grouping layer) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS mf_fulfillment_orders (
    fulfillment_id      VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    fulfillment_type    VARCHAR(30)     NOT NULL, -- PRODUCT|SERVICE|MIXED
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    provider_ref        VARCHAR(255),
    facility_id         UUID,
    vendor_id           UUID,
    metadata            JSONB,
    staged_at           TIMESTAMPTZ,
    packed_at           TIMESTAMPTZ,
    handed_off_at       TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mf_fulfillment_order ON mf_fulfillment_orders (order_id, status, created_at DESC);

-- ── Logistics Providers / Assets ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mf_logistics_providers (
    provider_id         VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    provider_type       VARCHAR(30)     NOT NULL, -- HUMAN_COURIER|DRONE|ROBOT|LOCKER|PARTNER|OTHER
    name                VARCHAR(200)    NOT NULL,
    supported_modes     JSONB           NOT NULL DEFAULT '[]',
    service_areas       JSONB           NOT NULL DEFAULT '[]',
    capabilities        JSONB           NOT NULL DEFAULT '{}',
    active              BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mf_delivery_assets (
    asset_id            VARCHAR(26)     PRIMARY KEY,
    provider_id         VARCHAR(26)     NOT NULL REFERENCES mf_logistics_providers(provider_id),
    asset_type          VARCHAR(30)     NOT NULL, -- HUMAN_COURIER|DRONE|ROBOT|VEHICLE|LOCKER|HUB|OTHER
    asset_code          VARCHAR(100)    NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'AVAILABLE',
    capability_summary  JSONB           NOT NULL DEFAULT '{}',
    telemetry_supported BOOLEAN         NOT NULL DEFAULT false,
    active              BOOLEAN         NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mf_assets_code ON mf_delivery_assets (provider_id, asset_code);

-- ── Delivery Plans / Legs / Missions ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mf_delivery_plans (
    plan_id             VARCHAR(26)     PRIMARY KEY,
    fulfillment_id      VARCHAR(26)     NOT NULL REFERENCES mf_fulfillment_orders(fulfillment_id),
    mode                VARCHAR(40)     NOT NULL, -- PICKUP|HUMAN_DELIVERY|DRONE_DELIVERY|ROBOT_DELIVERY|AUTONOMOUS_VEHICLE_DELIVERY|LOCKER_PICKUP|FACILITY_HANDOFF|COMMUNITY_HANDOFF|HYBRID
    origin              JSONB           NOT NULL DEFAULT '{}',
    destination         JSONB           NOT NULL DEFAULT '{}',
    status              VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    selected_provider_ref VARCHAR(255),
    estimated_window    JSONB           DEFAULT '{}',
    constraints_summary JSONB           DEFAULT '{}',
    metadata            JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mf_delivery_legs (
    leg_id              VARCHAR(26)     PRIMARY KEY,
    plan_id             VARCHAR(26)     NOT NULL REFERENCES mf_delivery_plans(plan_id),
    sequence_number     INT             NOT NULL,
    leg_type            VARCHAR(40)     NOT NULL DEFAULT 'DIRECT',
    origin              JSONB           NOT NULL DEFAULT '{}',
    destination         JSONB           NOT NULL DEFAULT '{}',
    mode                VARCHAR(40)     NOT NULL,
    assigned_provider_id VARCHAR(26),
    assigned_asset_id   VARCHAR(26),
    status              VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    estimated_departure TIMESTAMPTZ,
    estimated_arrival   TIMESTAMPTZ,
    actual_departure    TIMESTAMPTZ,
    actual_arrival      TIMESTAMPTZ,
    metadata            JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mf_delivery_legs_plan ON mf_delivery_legs (plan_id, sequence_number);

CREATE TABLE IF NOT EXISTS mf_delivery_missions (
    mission_id          VARCHAR(26)     PRIMARY KEY,
    leg_id              VARCHAR(26)     NOT NULL REFERENCES mf_delivery_legs(leg_id),
    mission_type        VARCHAR(40)     NOT NULL, -- DRONE_MISSION|ROBOT_MISSION|AUTONOMOUS_VEHICLE_MISSION|LOCKER_RELEASE
    mission_status      VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    command_ref         VARCHAR(255),
    telemetry_ref       VARCHAR(255),
    launch_at           TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    failure_reason      TEXT,
    metadata            JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Handoff, Custody, Proof, Exceptions ────────────────────────────────────
CREATE TABLE IF NOT EXISTS mf_handoff_events (
    handoff_id          VARCHAR(26)     PRIMARY KEY,
    fulfillment_id      VARCHAR(26)     REFERENCES mf_fulfillment_orders(fulfillment_id),
    leg_id              VARCHAR(26)     REFERENCES mf_delivery_legs(leg_id),
    handoff_type        VARCHAR(40)     NOT NULL,
    from_party          JSONB           NOT NULL DEFAULT '{}',
    to_party            JSONB           NOT NULL DEFAULT '{}',
    location            JSONB           NOT NULL DEFAULT '{}',
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    integrity_outcome   VARCHAR(40),
    notes               TEXT,
    metadata            JSONB           DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_mf_handoffs_fulfillment ON mf_handoff_events (fulfillment_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS mf_custody_records (
    custody_id          VARCHAR(26)     PRIMARY KEY,
    fulfillment_id      VARCHAR(26)     REFERENCES mf_fulfillment_orders(fulfillment_id),
    package_ref         VARCHAR(128),
    custodian_type      VARCHAR(40)     NOT NULL,
    custodian_ref       VARCHAR(255)    NOT NULL,
    from_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    to_at               TIMESTAMPTZ,
    condition_summary   JSONB           DEFAULT '{}',
    seal_status         VARCHAR(40),
    temperature_status  VARCHAR(40),
    metadata            JSONB           DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_mf_custody_fulfillment ON mf_custody_records (fulfillment_id, from_at DESC);

CREATE TABLE IF NOT EXISTS mf_proofs (
    proof_id            VARCHAR(26)     PRIMARY KEY,
    plan_id             VARCHAR(26)     REFERENCES mf_delivery_plans(plan_id),
    handoff_id          VARCHAR(26)     REFERENCES mf_handoff_events(handoff_id),
    proof_type          VARCHAR(40)     NOT NULL, -- OTP|QR|SIGNATURE|PHOTO|BIOMETRIC|LOCKER_RELEASE|TELEMETRY_CONFIRMATION|GEO_CONFIRMATION|DIGITAL_ACK
    proof_reference     VARCHAR(255)    NOT NULL,
    verification_outcome VARCHAR(40)    NOT NULL DEFAULT 'UNVERIFIED',
    verified_at         TIMESTAMPTZ,
    metadata            JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mf_proofs_plan ON mf_proofs (plan_id, created_at DESC);

CREATE TABLE IF NOT EXISTS mf_delivery_exceptions (
    exception_id        VARCHAR(26)     PRIMARY KEY,
    plan_id             VARCHAR(26)     REFERENCES mf_delivery_plans(plan_id),
    leg_id              VARCHAR(26)     REFERENCES mf_delivery_legs(leg_id),
    exception_type      VARCHAR(60)     NOT NULL,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'MAJOR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    raised_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolution_summary  TEXT,
    resolved_at         TIMESTAMPTZ,
    metadata            JSONB           DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_mf_exceptions_status ON mf_delivery_exceptions (status, raised_at DESC);

