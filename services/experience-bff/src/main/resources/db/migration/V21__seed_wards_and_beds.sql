-- V21: Seed wards and beds for the 10 golden-path facilities.
-- Creates realistic ward structures with bed inventory.

-- Helper: get facility IDs from seeded facilities
-- Harare Central Hospital
INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, floor, total_beds, status)
SELECT gen_random_uuid(), 'tenant-moh-zw', f.id, w.name, w.ward_type, w.floor, w.total_beds, 'ACTIVE'
FROM facilities f
CROSS JOIN (VALUES
    ('Medical Ward A', 'MEDICAL', '1st Floor', 8),
    ('Medical Ward B', 'MEDICAL', '1st Floor', 8),
    ('Surgical Ward', 'SURGICAL', '2nd Floor', 6),
    ('ICU', 'ICU', '2nd Floor', 4),
    ('Maternity Ward', 'MATERNITY', '3rd Floor', 10),
    ('Pediatric Ward', 'PEDIATRIC', '3rd Floor', 8),
    ('Emergency Observation', 'EMERGENCY', 'Ground Floor', 6)
) AS w(name, ward_type, floor, total_beds)
WHERE f.name = 'Harare Central Hospital';

-- Parirenyatwa Group of Hospitals
INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, floor, total_beds, status)
SELECT gen_random_uuid(), 'tenant-moh-zw', f.id, w.name, w.ward_type, w.floor, w.total_beds, 'ACTIVE'
FROM facilities f
CROSS JOIN (VALUES
    ('General Ward', 'GENERAL', '1st Floor', 10),
    ('Surgical Ward', 'SURGICAL', '2nd Floor', 8),
    ('ICU', 'ICU', '2nd Floor', 3),
    ('Maternity', 'MATERNITY', '1st Floor', 8),
    ('Pediatrics', 'PEDIATRIC', '3rd Floor', 6)
) AS w(name, ward_type, floor, total_beds)
WHERE f.name = 'Parirenyatwa Group of Hospitals';

-- Chitungwiza Central Hospital
INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, floor, total_beds, status)
SELECT gen_random_uuid(), 'tenant-moh-zw', f.id, w.name, w.ward_type, w.floor, w.total_beds, 'ACTIVE'
FROM facilities f
CROSS JOIN (VALUES
    ('Medical Ward', 'MEDICAL', '1st Floor', 8),
    ('Surgical Ward', 'SURGICAL', '1st Floor', 6),
    ('Maternity', 'MATERNITY', '2nd Floor', 8),
    ('Pediatrics', 'PEDIATRIC', '2nd Floor', 6)
) AS w(name, ward_type, floor, total_beds)
WHERE f.name = 'Chitungwiza Central Hospital';

-- Smaller facilities: 2 wards each
INSERT INTO wards (id, tenant_id, facility_id, name, ward_type, floor, total_beds, status)
SELECT gen_random_uuid(), 'tenant-moh-zw', f.id, w.name, w.ward_type, w.floor, w.total_beds, 'ACTIVE'
FROM facilities f
CROSS JOIN (VALUES
    ('General Ward', 'GENERAL', 'Ground Floor', 6),
    ('Maternity', 'MATERNITY', 'Ground Floor', 4)
) AS w(name, ward_type, floor, total_beds)
WHERE f.name IN ('Mpilo Central Hospital', 'United Bulawayo Hospitals',
    'Mutare Provincial Hospital', 'Gweru Provincial Hospital',
    'Masvingo Provincial Hospital', 'Bindura Provincial Hospital',
    'Chinhoyi Provincial Hospital');

-- Now create beds for all wards
INSERT INTO beds (id, tenant_id, facility_id, ward_id, bed_number, bed_type, status)
SELECT gen_random_uuid(), w.tenant_id, w.facility_id, w.id,
       w.name || '-' || LPAD(s.n::text, 2, '0'),
       CASE w.ward_type WHEN 'ICU' THEN 'ICU' WHEN 'MATERNITY' THEN 'MATERNITY' ELSE 'STANDARD' END,
       'AVAILABLE'
FROM wards w
CROSS JOIN generate_series(1, 10) AS s(n)
WHERE s.n <= w.total_beds;
