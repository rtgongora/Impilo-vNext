-- =============================================================================
-- OROS — Orders & Results Orchestration Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Orders linked to PCT journeys; lab and pharmacy order samples per patient
-- Order IDs use ULID format (26-char); zibo_order_code uses lab-tests-zw codes
-- =============================================================================

\c oros

INSERT INTO oros_orders
    (order_id, tenant_id, facility_id, patient_cpid, order_type, priority,
     status, placed_by, workspace_id, encounter_ref, zibo_order_code)
VALUES
    ('01OR0000000000000000000001',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'CPID-ZW-00001',
     'LAB', 'ROUTINE', 'PLACED',
     'c0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     '01HW0000000000000000000001',
     'FBC'),

    ('01OR0000000000000000000002',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'CPID-ZW-00001',
     'LAB', 'ROUTINE', 'PLACED',
     'c0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     '01HW0000000000000000000001',
     'MALA-RDT'),

    ('01OR0000000000000000000003',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'CPID-ZW-00002',
     'LAB', 'ROUTINE', 'RESULT_AVAILABLE',
     'c0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     '01HW0000000000000000000002',
     'RBS'),

    ('01OR0000000000000000000004',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'CPID-ZW-00002',
     'PHARMACY', 'ROUTINE', 'PLACED',
     'c0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     '01HW0000000000000000000002',
     'METF-500'),

    ('01OR0000000000000000000005',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000004',
     'CPID-ZW-00003',
     'LAB', 'URGENT', 'IN_PROGRESS',
     'c0000000-0000-4000-8000-000000000003',
     'a0000000-0000-4000-8000-000000000004',
     '01HW0000000000000000000003',
     'HIV-RDT'),

    ('01OR0000000000000000000006',
     '00000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000005',
     'CPID-ZW-00005',
     'LAB', 'ROUTINE', 'PLACED',
     'c0000000-0000-4000-8000-000000000003',
     'a0000000-0000-4000-8000-000000000005',
     '01HW0000000000000000000005',
     'SPUTUM-AFB');

INSERT INTO oros_order_items
    (order_id, code, display_name, quantity)
VALUES
    ('01OR0000000000000000000001', 'FBC',      'Full Blood Count',              1),
    ('01OR0000000000000000000002', 'MALA-RDT', 'Malaria Rapid Diagnostic Test', 1),
    ('01OR0000000000000000000003', 'RBS',      'Random Blood Sugar',            1),
    ('01OR0000000000000000000004', 'METF-500', 'Metformin 500mg tablet',        60),
    ('01OR0000000000000000000005', 'HIV-RDT',  'HIV Rapid Test',                1),
    ('01OR0000000000000000000006', 'SPUTUM-AFB', 'Sputum for AFB (TB)',          3);

INSERT INTO oros_worksteps
    (order_id, step_type, status, assigned_to)
VALUES
    ('01OR0000000000000000000001', 'SAMPLE_COLLECTION', 'PENDING',   NULL),
    ('01OR0000000000000000000002', 'SAMPLE_COLLECTION', 'PENDING',   NULL),
    ('01OR0000000000000000000003', 'ANALYSIS',          'COMPLETED', 'lab.tech.001'),
    ('01OR0000000000000000000004', 'DISPENSE',          'PENDING',   NULL),
    ('01OR0000000000000000000005', 'SAMPLE_COLLECTION', 'IN_PROGRESS', 'lab.tech.002'),
    ('01OR0000000000000000000006', 'SAMPLE_COLLECTION', 'PENDING',   NULL);

INSERT INTO oros_results
    (order_id, kind, summary, zibo_result_codes, is_critical, reported_by)
VALUES
    ('01OR0000000000000000000003',
     'LAB',
     '{"test":"RBS","value":12.8,"unit":"mmol/L","referenceRange":"4.0-6.0","abnormalFlag":"HIGH","interpretation":"Above normal range — consistent with uncontrolled diabetes"}'::jsonb,
     'RBS',
     false,
     'lab.tech.001');
