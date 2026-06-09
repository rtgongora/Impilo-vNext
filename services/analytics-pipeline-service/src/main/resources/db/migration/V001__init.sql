CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.telemedicine_events (
    event_id        UUID            PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    event_type      VARCHAR(64)     NOT NULL,
    facility_id     VARCHAR(64),
    payload_json    TEXT,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_tm_events_tenant ON analytics.telemedicine_events (tenant_id);
CREATE INDEX idx_analytics_tm_events_type ON analytics.telemedicine_events (tenant_id, event_type);
CREATE INDEX idx_analytics_tm_events_received ON analytics.telemedicine_events (received_at DESC);
