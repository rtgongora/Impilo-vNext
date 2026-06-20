-- rtc-gateway-service V001 — persisted RTC sessions + transactional outbox
CREATE SCHEMA IF NOT EXISTS rtc;

CREATE TABLE rtc.rtc_sessions (
    id              VARCHAR(255)    PRIMARY KEY,
    tenant_id       VARCHAR(255)    NOT NULL,
    provider        VARCHAR(64)     NOT NULL,
    room_name       VARCHAR(255)    NOT NULL,
    room_url        TEXT            NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    patient_id      VARCHAR(255),
    provider_id     VARCHAR(255),
    encounter_id    VARCHAR(255),
    referral_id     VARCHAR(255),
    channel         VARCHAR(255),
    capabilities    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    media_policy    JSONB           NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rtc_sessions_tenant ON rtc.rtc_sessions (tenant_id, updated_at DESC);
CREATE INDEX idx_rtc_sessions_status ON rtc.rtc_sessions (tenant_id, status);

CREATE TABLE rtc.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_rtc_outbox_unpublished
    ON rtc.event_outbox (created_at)
    WHERE published_at IS NULL;
