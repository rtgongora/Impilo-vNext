-- ============================================================
-- Observability Service V002 — Ops health & heartbeat tables
-- ============================================================

-- 1. Service heartbeats — last-known-alive signal per service
CREATE TABLE obs.service_heartbeats (
    id              BIGSERIAL       PRIMARY KEY,
    service_name    VARCHAR(128)    NOT NULL,
    instance_id     VARCHAR(255)    NOT NULL,
    tenant_id       UUID            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'UP',
    version_tag     VARCHAR(64),
    metadata        JSONB           NOT NULL DEFAULT '{}',
    last_heartbeat  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_obs_heartbeat_instance UNIQUE (service_name, instance_id)
);

CREATE INDEX idx_obs_heartbeats_service ON obs.service_heartbeats (service_name);
CREATE INDEX idx_obs_heartbeats_tenant ON obs.service_heartbeats (tenant_id);
CREATE INDEX idx_obs_heartbeats_last ON obs.service_heartbeats (last_heartbeat);
