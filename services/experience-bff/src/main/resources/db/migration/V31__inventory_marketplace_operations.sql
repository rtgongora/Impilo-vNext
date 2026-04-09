-- V31: Inventory operations + marketplace provider-plane seeds

CREATE TABLE IF NOT EXISTS inventory_stock_counts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    count_date      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    counted_by      VARCHAR(255) NOT NULL,
    item_count      INTEGER NOT NULL DEFAULT 0,
    discrepancies   INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inventory_stock_counts_tenant_facility
    ON inventory_stock_counts (tenant_id, facility_id, count_date DESC);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    item_name       VARCHAR(500) NOT NULL,
    from_location   VARCHAR(255),
    to_location     VARCHAR(255),
    quantity        INTEGER NOT NULL DEFAULT 0,
    reason          VARCHAR(255),
    performed_by    VARCHAR(255) NOT NULL,
    moved_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_tenant_facility
    ON inventory_movements (tenant_id, facility_id, moved_at DESC);

CREATE TABLE IF NOT EXISTS inventory_requisitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255) NOT NULL,
    facility_id         UUID NOT NULL,
    requisition_number  VARCHAR(100) NOT NULL,
    requested_by        VARCHAR(255) NOT NULL,
    item_count          INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    needed_by           DATE,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_inventory_requisitions_number UNIQUE (tenant_id, requisition_number)
);

CREATE INDEX IF NOT EXISTS idx_inventory_requisitions_tenant_facility
    ON inventory_requisitions (tenant_id, facility_id, created_at DESC);

INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth, sex, facility_id, status)
VALUES
    ('91000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'CPID-TMZ-00001', 'Tariro', 'Moyo', '1992-01-12', 'FEMALE', 'a1b2c3d4-0001-4000-8000-000000000001', 'ACTIVE'),
    ('91000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'CPID-TMZ-00002', 'Simbarashe', 'Dube', '1988-09-03', 'MALE', 'a1b2c3d4-0001-4000-8000-000000000001', 'ACTIVE')
ON CONFLICT (tenant_id, cpid) DO NOTHING;

INSERT INTO inventory_items (id, tenant_id, facility_id, product_code, product_name, category, quantity_on_hand, reorder_level, unit, status, last_counted_at)
VALUES
    ('e2000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'PARA-500', 'Paracetamol 500mg', 'Essential Medicines', 1450, 200, 'TABLET', 'ACTIVE', NOW() - INTERVAL '2 days'),
    ('e2000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'AMOX-250', 'Amoxicillin 250mg', 'Antibiotics', 760, 120, 'CAPSULE', 'ACTIVE', NOW() - INTERVAL '5 days'),
    ('e2000000-0000-0000-0000-000000000003', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'SYRG-5ML', 'Disposable Syringe 5ml', 'Medical Supplies', 420, 500, 'EACH', 'LOW_STOCK', NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inventory_stock_counts (id, tenant_id, facility_id, count_date, counted_by, item_count, discrepancies, status, notes)
VALUES
    ('92000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', NOW() - INTERVAL '2 days', 'Sr. Chari', 128, 3, 'COMPLETED', 'Monthly pharmacy count closed with minor variance'),
    ('92000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', NOW() - INTERVAL '12 hours', 'T. Moyo', 24, 1, 'IN_PROGRESS', 'Emergency store spot-check in progress'),
    ('92000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', NOW() - INTERVAL '3 days', 'Pharmacy Lead', 96, 0, 'COMPLETED', 'Quarterly count reconciled')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inventory_movements (id, tenant_id, facility_id, item_name, from_location, to_location, quantity, reason, performed_by, moved_at)
VALUES
    ('93000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'Paracetamol 500mg', 'Central pharmacy store', 'Outpatient dispensary', 200, 'Clinic replenishment', 'T. Moyo', NOW() - INTERVAL '6 hours'),
    ('93000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'Disposable Syringe 5ml', 'Main supplies store', 'Emergency trolley room', 120, 'Trauma surge cover', 'S. Chari', NOW() - INTERVAL '1 day'),
    ('93000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'Amoxicillin 250mg', 'Bulk store', 'Pharmacy dispensary', 80, 'Weekly top-up', 'Dispensary team', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO inventory_requisitions (id, tenant_id, facility_id, requisition_number, requested_by, item_count, status, needed_by, notes)
VALUES
    ('94000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'REQ-HCH-20260408-01', 'Harare Central Pharmacy', 6, 'SUBMITTED', CURRENT_DATE + 2, 'Urgent antibiotics and IV fluids for OPD'),
    ('94000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'REQ-HCH-20260407-03', 'Emergency Unit', 3, 'APPROVED', CURRENT_DATE + 1, 'Restock emergency consumables'),
    ('94000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'REQ-SMC-20260406-02', 'Medical Stores', 4, 'FULFILLED', CURRENT_DATE, 'Routine weekly issue')
ON CONFLICT (tenant_id, requisition_number) DO NOTHING;

INSERT INTO marketplace_services (id, tenant_id, name, description, category, facility_id, facility_name, price, currency, available, rating, image_url)
VALUES
    ('95000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'Cold-chain vaccine delivery', 'Managed vaccine delivery with chain-of-custody tracking for district facilities.', 'Logistics', 'a1b2c3d4-0001-4000-8000-000000000001', 'Harare Central Hospital', 180.00, 'USD', TRUE, 4.70, NULL),
    ('95000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'Portable ultrasound servicing', 'Preventive maintenance and calibration for point-of-care ultrasound devices.', 'Biomedical', 'a1b2c3d4-0002-4000-8000-000000000002', 'Parirenyatwa Group of Hospitals', 420.00, 'USD', TRUE, 4.90, NULL),
    ('95000000-0000-0000-0000-000000000003', 'tenant-moh-zw', 'Laboratory courier run', 'Same-day specimen courier service between district labs and central processing.', 'Laboratory', 'a1b2c3d4-0001-4000-8000-000000000001', 'Harare Central Hospital', 95.00, 'USD', TRUE, 4.50, NULL),
    ('95000000-0000-0000-0000-000000000004', 'moh-zw', 'Oxygen plant preventive maintenance', 'Quarterly inspection and preventive maintenance for oxygen reticulation plant.', 'Engineering', 'f1000000-0000-0000-0000-000000000001', 'Sally Mugabe Central Hospital', 520.00, 'USD', TRUE, 4.80, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO service_requests (id, tenant_id, patient_id, service_id, service_name, facility_name, status, requested_at, scheduled_at, notes, tracking_number, created_at, updated_at)
VALUES
    ('96000000-0000-0000-0000-000000000001', 'tenant-moh-zw', '91000000-0000-0000-0000-000000000001', '95000000-0000-0000-0000-000000000001', 'Cold-chain vaccine delivery', 'Harare Central Hospital', 'CONFIRMED', NOW() - INTERVAL '1 day', NOW() + INTERVAL '1 day', 'District outreach replenishment before Friday clinic', 'BK-HCH-24001', NOW() - INTERVAL '1 day', NOW()),
    ('96000000-0000-0000-0000-000000000002', 'tenant-moh-zw', '91000000-0000-0000-0000-000000000002', '95000000-0000-0000-0000-000000000002', 'Portable ultrasound servicing', 'Parirenyatwa Group of Hospitals', 'PENDING', NOW() - INTERVAL '4 hours', NOW() + INTERVAL '3 days', 'Biomedical team requested maintenance during weekend downtime', 'BK-PGH-24002', NOW() - INTERVAL '4 hours', NOW()),
    ('96000000-0000-0000-0000-000000000003', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', '95000000-0000-0000-0000-000000000004', 'Oxygen plant preventive maintenance', 'Sally Mugabe Central Hospital', 'COMPLETED', NOW() - INTERVAL '6 days', NOW() - INTERVAL '3 days', 'Completed ahead of planned ward expansion', 'BK-SMC-24003', NOW() - INTERVAL '6 days', NOW() - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO marketplace_orders (id, tenant_id, facility_id, order_number, status, total_amount, currency, items, ordered_by, ordered_at, created_at, updated_at)
VALUES
    ('97000000-0000-0000-0000-000000000001', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'PO-HCH-20260408-01', 'PENDING', 295.00, 'USD', '[{"productId":"95000000-0000-0000-0000-000000000003","description":"Courier run x2","quantity":2,"unitPrice":95.00},{"productId":"95000000-0000-0000-0000-000000000001","description":"Cold-chain boxes","quantity":1,"unitPrice":105.00}]', 'Tariro Moyo', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '8 hours'),
    ('97000000-0000-0000-0000-000000000002', 'tenant-moh-zw', 'a1b2c3d4-0001-4000-8000-000000000001', 'PO-HCH-20260405-04', 'DELIVERED', 420.00, 'USD', '[{"productId":"95000000-0000-0000-0000-000000000002","description":"Ultrasound servicing","quantity":1,"unitPrice":420.00}]', 'Operations Desk', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '2 days'),
    ('97000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'PO-SMC-20260404-02', 'SHIPPED', 520.00, 'USD', '[{"productId":"95000000-0000-0000-0000-000000000004","description":"Oxygen plant maintenance","quantity":1,"unitPrice":520.00}]', 'Medical Stores', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
