-- Preview / E2E demo anchors — aligned with one-ui-shell api-client default tenant
-- and product-truth persistence proofs (Harare Central facility).

INSERT INTO inv_facilities (facility_id, tenant_id, name, facility_code, active)
VALUES (
    'a1b2c3d4-0001-4000-8000-000000000001',
    '00000000-0000-4000-8000-000000000001',
    'Harare Central Hospital',
    'HCH-001',
    TRUE
)
ON CONFLICT (facility_id) DO NOTHING;

INSERT INTO inv_stores (store_id, facility_id, tenant_id, name, store_type, active)
VALUES
    (
        '95000000-0000-0000-0000-000000000001',
        'a1b2c3d4-0001-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        'Pharmacy Store',
        'PHARMACY',
        TRUE
    ),
    (
        '95000000-0000-0000-0000-000000000002',
        'a1b2c3d4-0001-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        'Main Store',
        'GENERAL',
        TRUE
    )
ON CONFLICT (store_id) DO NOTHING;

INSERT INTO inv_items (item_id, tenant_id, item_code, name, uom, active)
VALUES (
    '96000000-0000-0000-0000-000000000001',
    '00000000-0000-4000-8000-000000000001',
    'PARACETAMOL-500',
    'Paracetamol 500mg',
    'TAB',
    TRUE
)
ON CONFLICT (tenant_id, item_code) DO NOTHING;
