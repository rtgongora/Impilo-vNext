-- Facility UID canonicalization (F0 — heal the facility ID seam).
--
-- The estate has three facility id-spaces: tuso bigint PK, tuso facility_uuid,
-- and the f1000000-… UUIDs that PCT queues / BFF golden-path seeds / staff
-- assignments key on. Canonical cross-service key = facility_uuid. This seed
-- makes the five seed facilities' facility_uuid BE the established f1000000
-- UUIDs, so existing operational data attaches to the registry without a
-- mapping layer. Imported facilities already carry their master-pack UUIDs.
--
-- Idempotent: only rewrites rows still carrying a non-canonical uuid.

UPDATE tuso.facility SET facility_uuid = 'f1000000-0000-0000-0000-000000000001'::uuid
 WHERE id = 1 AND facility_uuid IS DISTINCT FROM 'f1000000-0000-0000-0000-000000000001'::uuid;
UPDATE tuso.facility SET facility_uuid = 'f1000000-0000-0000-0000-000000000002'::uuid
 WHERE id = 2 AND facility_uuid IS DISTINCT FROM 'f1000000-0000-0000-0000-000000000002'::uuid;
UPDATE tuso.facility SET facility_uuid = 'f1000000-0000-0000-0000-000000000003'::uuid
 WHERE id = 3 AND facility_uuid IS DISTINCT FROM 'f1000000-0000-0000-0000-000000000003'::uuid;
UPDATE tuso.facility SET facility_uuid = 'f1000000-0000-0000-0000-000000000004'::uuid
 WHERE id = 4 AND facility_uuid IS DISTINCT FROM 'f1000000-0000-0000-0000-000000000004'::uuid;
UPDATE tuso.facility SET facility_uuid = 'f1000000-0000-0000-0000-000000000005'::uuid
 WHERE id = 5 AND facility_uuid IS DISTINCT FROM 'f1000000-0000-0000-0000-000000000005'::uuid;
