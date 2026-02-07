-- =============================================
-- Service : offline-sync-service
-- Schema  : offline_sync
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS offline_sync;

CREATE TABLE offline_sync.sync_pack (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    device_id       VARCHAR(128)    NOT NULL,
    pack_version    BIGINT          NOT NULL DEFAULT 1,
    status          VARCHAR(20)     DEFAULT 'PENDING',
    payload         JSONB,
    created_at      TIMESTAMPTZ     DEFAULT now(),
    synced_at       TIMESTAMPTZ
);

CREATE INDEX idx_sync_pack_tenant_facility_device ON offline_sync.sync_pack (tenant_id, facility_id, device_id);
