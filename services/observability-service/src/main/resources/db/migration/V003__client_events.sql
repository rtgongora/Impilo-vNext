-- ============================================================
-- Observability Service V003 — PII-free client-event sink
-- ============================================================
-- Allow-listed columns only. This sink stores telemetry
-- breadcrumbs (route + code + trace ids + status), never user
-- content. Do NOT add free-text message/body columns here.

CREATE TABLE obs.client_events (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID,
    route           VARCHAR(256),
    code            VARCHAR(64),
    correlation_id  VARCHAR(64),
    request_id      VARCHAR(64),
    http_status     INT,
    occurred_at     TIMESTAMPTZ,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    source          VARCHAR(32)     NOT NULL DEFAULT 'CLIENT'
);

CREATE INDEX idx_obs_client_events_code_received ON obs.client_events (code, received_at);
CREATE INDEX idx_obs_client_events_route ON obs.client_events (route);
