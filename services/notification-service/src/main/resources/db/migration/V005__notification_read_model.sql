-- V005: notification inbox read model and convenience indexes

ALTER TABLE ns_notifications
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_ns_notifications_tenant_read
    ON ns_notifications (tenant_id, read_at);
