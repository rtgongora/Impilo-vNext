-- =============================================================================
-- TUSO — Theatre service-point seed (operating-theatre product-truth, Wave 0)
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- A fresh estate has no theatre rooms, so the perioperative ROOM readiness gate
-- (inpatient TheatreService.evaluateReadiness → requires a theatre_room_id) can
-- never resolve READY and the elective happy path hard-blocks on NO_ROOM.
--
-- This seeds THEATRE service_points (3 per central hospital) so a booking clerk
-- can assign a real theatre-room id to the episode and readiness resolves ROOM.
-- These are the MINIMAL unblock — the first-class TUSO clinical-space / bed /
-- equipment model is Wave 2; this only seeds enough service_points to unblock.
--
-- Facility 1 = Harare Central Hospital; facility 2 = Parirenyatwa (seed 02).
-- Fixed UUIDs a3...0001-0006 so the id is stable across re-seeds and referenceable
-- from a booking. Idempotent via WHERE NOT EXISTS on the fixed id.
-- =============================================================================

\c tuso

-- Harare Central Hospital (facility_id = 1) — Theatres 1-3
INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000001'::uuid, 1,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Main Theatre 1', 'HCH-THEATRE-1', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000001'::uuid);

INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000002'::uuid, 1,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Main Theatre 2', 'HCH-THEATRE-2', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000002'::uuid);

INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000003'::uuid, 1,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Emergency Theatre', 'HCH-THEATRE-EMG', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000003'::uuid);

-- Parirenyatwa Group of Hospitals (facility_id = 2) — Theatres 1-3
INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000004'::uuid, 2,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Main Theatre 1', 'PGH-THEATRE-1', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000004'::uuid);

INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000005'::uuid, 2,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Main Theatre 2', 'PGH-THEATRE-2', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000005'::uuid);

INSERT INTO tuso.service_point
    (id, facility_id, tenant_id, name, code, service_point_type, status, active, created_by)
SELECT 'a3000000-0000-4000-8000-000000000006'::uuid, 2,
    '00000000-0000-4000-8000-000000000001'::uuid, 'Cardiothoracic Theatre', 'PGH-THEATRE-CT', 'THEATRE', 'ACTIVE', true, 'seed-theatre'
WHERE NOT EXISTS (SELECT 1 FROM tuso.service_point WHERE id = 'a3000000-0000-4000-8000-000000000006'::uuid);
