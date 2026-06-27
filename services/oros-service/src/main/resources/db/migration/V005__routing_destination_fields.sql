-- =============================================
-- Service : oros-service
-- Flyway  : V005__routing_destination_fields.sql
-- Purpose : Capture an explicit routing destination for diagnostic orders (spec §4 routing
--           details): internal department/service-point, external provider/facility, or
--           patient-carried, plus the expected result-return method and an optional route
--           expiry. Extends the existing oros_routing record (no new system of record).
-- =============================================

ALTER TABLE oros_routing
    ADD COLUMN destination_type          VARCHAR(32),
    -- INTERNAL_DEPARTMENT, INTERNAL_SERVICE_POINT, EXTERNAL_PROVIDER, EXTERNAL_FACILITY, PATIENT_CARRIED
    ADD COLUMN destination_facility_id   UUID,
    ADD COLUMN destination_department_id VARCHAR(64),
    ADD COLUMN destination_service_point_id VARCHAR(64),
    ADD COLUMN destination_provider_id   VARCHAR(64),
    ADD COLUMN destination_name          VARCHAR(255),
    ADD COLUMN expected_return_method    VARCHAR(32),
    -- IN_PLATFORM, SECURE_LINK, PATIENT_CARRIED, EXTERNAL_SYSTEM
    ADD COLUMN route_expiry              TIMESTAMPTZ;

CREATE INDEX idx_oros_routing_destination
    ON oros_routing (destination_type, destination_facility_id)
    WHERE destination_type IS NOT NULL;
