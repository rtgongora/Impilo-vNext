-- V11: Add tuso_id mapping columns for Kafka sync consumers
-- Allows mapping TUSO's internal Long primary keys to BFF UUID keys

ALTER TABLE facilities
    ADD COLUMN IF NOT EXISTS tuso_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_facilities_tuso_id
    ON facilities (tenant_id, tuso_id)
    WHERE tuso_id IS NOT NULL;

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS tuso_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_workspaces_tuso_id
    ON workspaces (tenant_id, tuso_id)
    WHERE tuso_id IS NOT NULL;
