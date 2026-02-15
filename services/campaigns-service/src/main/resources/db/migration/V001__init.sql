-- ============================================================
-- Campaigns Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

CREATE SCHEMA IF NOT EXISTS camp;

-- ----------------------------------------------------------
-- 1. campaigns — campaign definitions
-- ----------------------------------------------------------
CREATE TABLE camp.campaigns (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    campaign_type   VARCHAR(50)     NOT NULL DEFAULT 'OUTREACH',
    target_group    JSONB           NOT NULL DEFAULT '{}',
    message_template JSONB          NOT NULL DEFAULT '{}',
    channel         VARCHAR(50)     NOT NULL DEFAULT 'SMS',
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    start_date      DATE,
    end_date        DATE,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaigns_tenant ON camp.campaigns (tenant_id);
CREATE INDEX idx_campaigns_status ON camp.campaigns (tenant_id, status);
CREATE INDEX idx_campaigns_type ON camp.campaigns (tenant_id, campaign_type);

-- ----------------------------------------------------------
-- 2. enrollments — campaign participant enrollments (stub)
-- ----------------------------------------------------------
CREATE TABLE camp.enrollments (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    campaign_id     BIGINT          NOT NULL REFERENCES camp.campaigns(id),
    participant_id  VARCHAR(255)    NOT NULL,
    participant_type VARCHAR(50)    NOT NULL DEFAULT 'PATIENT',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ENROLLED',
    enrolled_by     VARCHAR(255)    NOT NULL,
    enrolled_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_enrollments_tenant ON camp.enrollments (tenant_id);
CREATE INDEX idx_enrollments_campaign ON camp.enrollments (campaign_id);
CREATE INDEX idx_enrollments_participant ON camp.enrollments (tenant_id, participant_id);

-- ----------------------------------------------------------
-- 3. deliveries — message delivery tracking (stub)
-- ----------------------------------------------------------
CREATE TABLE camp.deliveries (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    campaign_id     BIGINT          NOT NULL REFERENCES camp.campaigns(id),
    enrollment_id   BIGINT          REFERENCES camp.enrollments(id),
    channel         VARCHAR(50)     NOT NULL DEFAULT 'SMS',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    dispatched_at   TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_deliveries_tenant ON camp.deliveries (tenant_id);
CREATE INDEX idx_deliveries_campaign ON camp.deliveries (campaign_id);
CREATE INDEX idx_deliveries_status ON camp.deliveries (tenant_id, status);

-- ----------------------------------------------------------
-- 4. event_outbox — transactional outbox for Kafka
-- ----------------------------------------------------------
CREATE TABLE camp.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       VARCHAR(255),
    pod_id          VARCHAR(255),
    correlation_id  VARCHAR(255),
    idempotency_key VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_camp_outbox_unpublished
    ON camp.event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 5. idempotency_keys — request dedup
-- ----------------------------------------------------------
CREATE TABLE camp.idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_camp_idempotency_key ON camp.idempotency_keys (idempotency_key);
