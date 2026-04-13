-- V16: Add TUSO booking bridge column and enhance appointments table.
-- Links BFF appointments with TUSO's BookingService for resource-level
-- conflict detection and calendar management.

ALTER TABLE appointments ADD COLUMN IF NOT EXISTS tuso_booking_id UUID;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS end_at TIMESTAMPTZ;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS provider_id UUID;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS provider_name VARCHAR(500);
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE INDEX IF NOT EXISTS idx_appointments_tuso_booking ON appointments(tuso_booking_id);
CREATE INDEX IF NOT EXISTS idx_appointments_facility ON appointments(tenant_id, facility_id);
CREATE INDEX IF NOT EXISTS idx_appointments_scheduled ON appointments(tenant_id, scheduled_at);
