-- ============================================================================
-- On-call seed for tenant-moh-zw + V2 Harare Central (UI default tenant / facility list)
-- V29 seeds moh-zw + golden-path facility UUID; this aligns dev UX with api-client default.
-- ============================================================================

INSERT INTO on_call_assignments (
    id, tenant_id, facility_id, assignment_date, specialty, shift_kind,
    primary_staff_name, primary_phone, backup_staff_name, backup_phone
) VALUES
(
    'f2000000-0000-0000-0000-000000000101',
    'tenant-moh-zw',
    'a1b2c3d4-0001-4000-8000-000000000001',
    DATE '2026-04-06',
    'Internal Medicine',
    '24hr',
    'Dr. Tendai Mapfumo',
    '+263771111111',
    'Dr. Grace Musekwa',
    '+263772222222'
),
(
    'f2000000-0000-0000-0000-000000000102',
    'tenant-moh-zw',
    'a1b2c3d4-0001-4000-8000-000000000001',
    DATE '2026-04-07',
    'Surgery',
    '24hr',
    'Dr. Simba Nyamukapa',
    '+263773333333',
    'Dr. Tendai Mapfumo',
    '+263771111111'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO on_call_swap_requests (
    id, tenant_id, facility_id, requestor_name, requestee_name,
    original_date, swap_date, specialty, status
) VALUES
(
    'f2000000-0000-0000-0000-000000000110',
    'tenant-moh-zw',
    'a1b2c3d4-0001-4000-8000-000000000001',
    'Dr. Tendai Mapfumo',
    'Dr. Grace Musekwa',
    DATE '2026-04-10',
    DATE '2026-04-12',
    'Internal Medicine',
    'PENDING'
)
ON CONFLICT (id) DO NOTHING;
