-- ============================================================================
-- Experience BFF — Golden Path Seed Data
-- V4: Seed patients, queue entries, shifts, admin users, providers
-- ============================================================================

-- Seed patients (for golden path C: clinical workflow)
INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth, sex, national_id, phone, facility_id) VALUES
('a1000000-0000-0000-0000-000000000001', 'moh-zw', 'CPID-ZW-00001', 'Tatenda', 'Moyo', '1990-03-15', 'MALE', '63-123456-A-78', '+263771234567', 'f1000000-0000-0000-0000-000000000001'),
('a1000000-0000-0000-0000-000000000002', 'moh-zw', 'CPID-ZW-00002', 'Rudo', 'Chikwanha', '1985-07-22', 'FEMALE', '63-234567-B-89', '+263772345678', 'f1000000-0000-0000-0000-000000000001'),
('a1000000-0000-0000-0000-000000000003', 'moh-zw', 'CPID-ZW-00003', 'Tinashe', 'Madziva', '2000-01-10', 'MALE', '63-345678-C-90', '+263773456789', 'f1000000-0000-0000-0000-000000000002'),
('a1000000-0000-0000-0000-000000000004', 'moh-zw', 'CPID-ZW-00004', 'Nyasha', 'Sibanda', '1978-11-28', 'FEMALE', '63-456789-D-01', '+263774567890', 'f1000000-0000-0000-0000-000000000002'),
('a1000000-0000-0000-0000-000000000005', 'moh-zw', 'CPID-ZW-00005', 'Kudakwashe', 'Dube', '1995-05-03', 'MALE', '63-567890-E-12', '+263775678901', 'f1000000-0000-0000-0000-000000000003');

-- Seed queue entries (for golden path C: Queue → Encounter)
INSERT INTO queue_entries (id, tenant_id, facility_id, patient_id, queue_type, priority, status, reason) VALUES
('b1000000-0000-0000-0000-000000000001', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'WALK_IN', 'NORMAL', 'WAITING', 'General consultation'),
('b1000000-0000-0000-0000-000000000002', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000002', 'SCHEDULED', 'NORMAL', 'WAITING', 'Follow-up visit'),
('b1000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000003', 'WALK_IN', 'URGENT', 'WAITING', 'Acute symptoms');

-- Seed admin users (for golden path D: Admin → TSHEPO governance)
INSERT INTO admin_users (id, tenant_id, username, email, display_name, role, facility_id) VALUES
('c1000000-0000-0000-0000-000000000001', 'moh-zw', 'admin.central', 'admin@mohcc.gov.zw', 'Central Administrator', 'SYSTEM_ADMIN', NULL),
('c1000000-0000-0000-0000-000000000002', 'moh-zw', 'admin.harare', 'harare.admin@mohcc.gov.zw', 'Harare Province Admin', 'FACILITY_ADMIN', 'f1000000-0000-0000-0000-000000000001'),
('c1000000-0000-0000-0000-000000000003', 'moh-zw', 'dr.mapfumo', 'mapfumo@mohcc.gov.zw', 'Dr. Tendai Mapfumo', 'CLINICIAN', 'f1000000-0000-0000-0000-000000000001'),
('c1000000-0000-0000-0000-000000000004', 'moh-zw', 'nurse.chienda', 'chienda@mohcc.gov.zw', 'Nurse Chienda', 'CLINICIAN', 'f1000000-0000-0000-0000-000000000002');

-- Seed registry providers (for golden path F: Registry data)
INSERT INTO registry_providers (id, tenant_id, provider_number, given_name, family_name, qualification, speciality, facility_id) VALUES
('d1000000-0000-0000-0000-000000000001', 'moh-zw', 'PROV-ZW-001', 'Tendai', 'Mapfumo', 'MBChB', 'General Practice', 'f1000000-0000-0000-0000-000000000001'),
('d1000000-0000-0000-0000-000000000002', 'moh-zw', 'PROV-ZW-002', 'Grace', 'Musekwa', 'BSc Nursing', 'Primary Care', 'f1000000-0000-0000-0000-000000000001'),
('d1000000-0000-0000-0000-000000000003', 'moh-zw', 'PROV-ZW-003', 'Simba', 'Nyamukapa', 'MBChB, MMed', 'Surgery', 'f1000000-0000-0000-0000-000000000002');

-- Seed inventory items
INSERT INTO inventory_items (id, tenant_id, facility_id, product_code, product_name, category, quantity_on_hand, reorder_level, unit) VALUES
('e1000000-0000-0000-0000-000000000001', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'PARA-500', 'Paracetamol 500mg', 'Essential Medicines', 1500, 200, 'TABLET'),
('e1000000-0000-0000-0000-000000000002', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'AMOX-250', 'Amoxicillin 250mg', 'Antibiotics', 800, 100, 'CAPSULE'),
('e1000000-0000-0000-0000-000000000003', 'moh-zw', 'f1000000-0000-0000-0000-000000000001', 'SYRG-5ML', 'Disposable Syringe 5ml', 'Medical Supplies', 2000, 500, 'EACH');
