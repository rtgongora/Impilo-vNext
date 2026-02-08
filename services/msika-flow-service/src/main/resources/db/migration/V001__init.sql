-- =============================================
-- Service : msika-flow-service (Health Marketplace Orchestration)
-- Schema  : public (mf_ prefix convention)
-- Flyway  : V001__init.sql
-- Tables  : 13
-- =============================================

-- ── Orders ────────────────────────────────────────────────────────────────
CREATE TABLE mf_orders (
    order_id            VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(128)    NOT NULL,
    actor_type          VARCHAR(20)     NOT NULL DEFAULT 'PATIENT',
    patient_cpid        VARCHAR(64),
    order_type          VARCHAR(30)     NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'CREATED',
    facility_id         UUID,
    vendor_id           UUID,
    amount_total        NUMERIC(14,2)   NOT NULL DEFAULT 0,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'ZWG',
    price_snapshot      JSONB,
    restrictions_snapshot JSONB,
    idempotency_key     VARCHAR(128)    UNIQUE,
    lock_version        INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Order Lines ───────────────────────────────────────────────────────────
CREATE TABLE mf_order_lines (
    line_id             VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    msika_core_code     VARCHAR(128)    NOT NULL,
    kind                VARCHAR(10)     NOT NULL DEFAULT 'PRODUCT',
    qty                 INT             NOT NULL DEFAULT 1,
    unit_price          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    line_total          NUMERIC(14,2)   NOT NULL DEFAULT 0,
    restrictions        JSONB,
    fulfillment_mode    VARCHAR(30),
    substitution_policy JSONB,
    state               VARCHAR(30)     NOT NULL DEFAULT 'REQUESTED',
    metadata            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Order Events (audit trail) ────────────────────────────────────────────
CREATE TABLE mf_order_events (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    from_state          VARCHAR(30),
    to_state            VARCHAR(30)     NOT NULL,
    actor_id            VARCHAR(128)    NOT NULL,
    actor_type          VARCHAR(20)     NOT NULL,
    reason_code         VARCHAR(50),
    note                TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Fulfillment Routes ────────────────────────────────────────────────────
CREATE TABLE mf_fulfillment_routes (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    route_type          VARCHAR(30)     NOT NULL,
    target_ref          JSONB,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    last_error          TEXT,
    retry_count         INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Reservations (inventory/pharmacy holds) ───────────────────────────────
CREATE TABLE mf_reservations (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    line_id             VARCHAR(26)     NOT NULL REFERENCES mf_order_lines(line_id),
    system              VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    reservation_ref     VARCHAR(128),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Pickup Tokens ─────────────────────────────────────────────────────────
CREATE TABLE mf_pickup_tokens (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    token_hash          VARCHAR(128)    NOT NULL UNIQUE,
    otp_hash            VARCHAR(128)    NOT NULL,
    expires_at          TIMESTAMPTZ     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    claimed_by          VARCHAR(128),
    claimed_actor_type  VARCHAR(20),
    claimed_at          TIMESTAMPTZ,
    claim_meta          JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Vendor Profiles ───────────────────────────────────────────────────────
CREATE TABLE mf_vendor_profiles (
    vendor_id           VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'APPLIED',
    coverage            JSONB,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Vendor Documents ──────────────────────────────────────────────────────
CREATE TABLE mf_vendor_documents (
    id                  VARCHAR(26)     PRIMARY KEY,
    vendor_id           VARCHAR(26)     NOT NULL REFERENCES mf_vendor_profiles(vendor_id),
    doc_type            VARCHAR(50)     NOT NULL,
    landela_doc_id      VARCHAR(128)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'UPLOADED',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Settlements ───────────────────────────────────────────────────────────
CREATE TABLE mf_settlements (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    mushex_payment_intent_id VARCHAR(128) NOT NULL UNIQUE,
    splits              JSONB,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Refunds ───────────────────────────────────────────────────────────────
CREATE TABLE mf_refunds (
    id                  VARCHAR(26)     PRIMARY KEY,
    order_id            VARCHAR(26)     NOT NULL REFERENCES mf_orders(order_id),
    amount              NUMERIC(14,2)   NOT NULL,
    reason              VARCHAR(200)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED',
    mushex_refund_id    VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Ops Reviews ───────────────────────────────────────────────────────────
CREATE TABLE mf_ops_reviews (
    id                  VARCHAR(26)     PRIMARY KEY,
    entity_type         VARCHAR(50)     NOT NULL,
    entity_id           VARCHAR(128)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assigned_to         VARCHAR(128),
    notes               TEXT,
    tenant_id           UUID            NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Capability Profiles (routing decisions) ───────────────────────────────
CREATE TABLE mf_capability_profiles (
    id                  VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    facility_id         UUID,
    profile             JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Event Outbox (mandatory for Kafka) ────────────────────────────────────
CREATE TABLE mf_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    aggregate_type      VARCHAR(50)     NOT NULL,
    aggregate_id        VARCHAR(128)    NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    payload             JSONB           NOT NULL,
    tenant_id           UUID            NOT NULL,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ── Indexes ───────────────────────────────────────────────────────────────
CREATE INDEX idx_mf_orders_tenant_status ON mf_orders (tenant_id, status, created_at DESC);
CREATE INDEX idx_mf_orders_patient ON mf_orders (patient_cpid, created_at DESC);
CREATE INDEX idx_mf_orders_facility ON mf_orders (facility_id, status);
CREATE INDEX idx_mf_orders_vendor ON mf_orders (vendor_id, status);
CREATE INDEX idx_mf_order_lines_order ON mf_order_lines (order_id);
CREATE INDEX idx_mf_order_events_order ON mf_order_events (order_id, created_at);
CREATE INDEX idx_mf_fulfillment_order ON mf_fulfillment_routes (order_id);
CREATE INDEX idx_mf_reservations_order ON mf_reservations (order_id);
CREATE INDEX idx_mf_pickup_tokens_order ON mf_pickup_tokens (order_id);
CREATE INDEX idx_mf_vendor_profiles_tenant ON mf_vendor_profiles (tenant_id, status);
CREATE INDEX idx_mf_vendor_docs_vendor ON mf_vendor_documents (vendor_id);
CREATE INDEX idx_mf_settlements_order ON mf_settlements (order_id);
CREATE INDEX idx_mf_refunds_order ON mf_refunds (order_id);
CREATE INDEX idx_mf_ops_reviews_status ON mf_ops_reviews (status, created_at);
CREATE INDEX idx_mf_capability_tenant ON mf_capability_profiles (tenant_id, facility_id);
CREATE INDEX idx_mf_outbox_unpublished ON mf_event_outbox (created_at) WHERE published_at IS NULL;
