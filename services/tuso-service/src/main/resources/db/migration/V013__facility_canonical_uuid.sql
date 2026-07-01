-- Facility lifecycle wave: canonical facility UUID (Option 1).
--
-- TUSO facility identity is a numeric Long PK, but downstream services (e.g. PCT) key their
-- materialised facility-scoped records (queues, care locations) by UUID. This introduces a single
-- canonical facility UUID that TUSO owns and that downstream services can key off — aligning with
-- the "one anchor, many IDs" doctrine and removing the need for ad-hoc TUSO-Long <-> PCT-UUID
-- correlation. Additive and backfilled, so it cannot break existing rows.

ALTER TABLE tuso.facility
    ADD COLUMN IF NOT EXISTS facility_uuid UUID;

-- Backfill existing facilities with a stable UUID.
UPDATE tuso.facility
   SET facility_uuid = gen_random_uuid()
 WHERE facility_uuid IS NULL;

-- New rows get one automatically even if a code path forgets to set it.
ALTER TABLE tuso.facility
    ALTER COLUMN facility_uuid SET DEFAULT gen_random_uuid();

ALTER TABLE tuso.facility
    ALTER COLUMN facility_uuid SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_facility_facility_uuid
    ON tuso.facility (facility_uuid);
