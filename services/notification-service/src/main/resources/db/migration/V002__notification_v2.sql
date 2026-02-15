-- V002: Upgrade notification-service to communications hub
-- Adds template key/subject, notification queue with status transitions

-- ===== Upgrade ns_templates =====
ALTER TABLE ns_templates ADD COLUMN key VARCHAR(128);
ALTER TABLE ns_templates ADD COLUMN subject VARCHAR(512);

-- Backfill key from name for existing rows
UPDATE ns_templates SET key = name WHERE key IS NULL;

ALTER TABLE ns_templates ALTER COLUMN key SET NOT NULL;
CREATE UNIQUE INDEX idx_ns_templates_key ON ns_templates (key);

-- ===== Create notification queue =====
CREATE TABLE ns_notifications (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    template_key    VARCHAR(128),
    channel         VARCHAR(32)     NOT NULL,
    to_addr         VARCHAR(256)    NOT NULL,
    vars_json       TEXT,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    attempts        INT             NOT NULL DEFAULT 0,
    last_error      TEXT,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_ns_notifications_status ON ns_notifications (status, created_at);
CREATE INDEX idx_ns_notifications_tenant ON ns_notifications (tenant_id);
