-- =============================================
-- Service : tuso-service
-- Schema  : tuso
-- Flyway  : V005__facility_operating_profile.sql
-- Purpose : Persist facility operating model metadata
-- =============================================

ALTER TABLE tuso.facility
    ADD COLUMN IF NOT EXISTS facility_tier VARCHAR(64),
    ADD COLUMN IF NOT EXISTS deployment_mode VARCHAR(32),
    ADD COLUMN IF NOT EXISTS continuity_class VARCHAR(32),
    ADD COLUMN IF NOT EXISTS workflow_archetype VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_facility_tier ON tuso.facility (facility_tier);
CREATE INDEX IF NOT EXISTS idx_facility_deployment_mode ON tuso.facility (deployment_mode);
