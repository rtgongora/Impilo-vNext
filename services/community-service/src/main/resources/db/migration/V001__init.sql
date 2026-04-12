-- =============================================================================
-- Community Service — initial schema (community health / CHW / outreach)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS community;

CREATE TABLE community.community_units (
    id              BIGSERIAL PRIMARY KEY,
    unit_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    unit_type       VARCHAR(32) NOT NULL,
    facility_id     UUID,
    province        VARCHAR(128),
    district        VARCHAR(128),
    ward            VARCHAR(128),
    catchment_population INT,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE community.chw_assignments (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    unit_id         UUID NOT NULL REFERENCES community.community_units(unit_id),
    provider_id     VARCHAR(128) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'CHW',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE community.outreach_visits (
    id              BIGSERIAL PRIMARY KEY,
    visit_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    unit_id         UUID NOT NULL REFERENCES community.community_units(unit_id),
    patient_cpid    VARCHAR(128) NOT NULL,
    provider_id     VARCHAR(128) NOT NULL,
    journey_id      UUID,
    encounter_id    UUID,
    visit_type      VARCHAR(32) NOT NULL,
    location_lat    DECIMAL(10,7),
    location_lon    DECIMAL(10,7),
    status          VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE community.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'community-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_community_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_community_outbox_unpublished ON community.event_outbox (created_at) WHERE published_at IS NULL;
