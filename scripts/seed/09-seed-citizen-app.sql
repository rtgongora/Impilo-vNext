-- =============================================================================
-- Citizen App Demo Seed Data  (09-seed-citizen-app.sql)
-- =============================================================================
-- Citizen under test : Tatenda Moyo
--   CPID             : CPID-ZW-00001
--   Health UUID      : b0000000-0000-4000-8000-000000000001
--   Tenant UUID      : 00000000-0000-4000-8000-000000000001
--   Tenant string    : tenant-moh-zw
--   Login            : citizen.moyo / test123
--
-- What is seeded here
-- ────────────────────────────────────────────────────────────────────────────
--  ✅ tuso           — resource catalog + 4 bookings (future citizen endpoint)
--  ✅ experience_bff — patient index, consent preferences, BFF coverage plan,
--                      marketplace services (citizen-facing)
--
-- What is NOT seeded (services not in docker-compose.runtime.yml)
-- ────────────────────────────────────────────────────────────────────────────
--  ⏸  impilo_coverage — coverage-service not deployed; tables don't exist yet
--  ⏸  pharmacy        — pharmacy-service not deployed; tables don't exist yet
--
-- Notes
-- ────────────────────────────────────────────────────────────────────────────
--  • feed_items are already seeded by experience-bff V24 Flyway migration
--  • VITO, OROS, PCT seeds are in their own files (03, 07, 08)
--  • TUSO booking seed is forward-compatible for when TUSO adds
--    GET /v1/appointments/citizen/{cpid} (currently missing endpoint)
-- =============================================================================


-- ─────────────────────────────────────────────────────────────────────────────
-- 1. TUSO — Resource catalog + citizen bookings
-- ─────────────────────────────────────────────────────────────────────────────
\c tuso

INSERT INTO tuso.resource
    (id, facility_id, tenant_id, name, resource_type, capacity, status)
VALUES
    ('d1000000-0000-4000-8000-000000000001',
     1,
     '00000000-0000-4000-8000-000000000001',
     'Dr Chipo Mlambo — OPD Room 3',
     'CLINICIAN', 1, 'ACTIVE'),

    ('d1000000-0000-4000-8000-000000000002',
     1,
     '00000000-0000-4000-8000-000000000001',
     'Dr Farai Ndlovu — OPD Room 5',
     'CLINICIAN', 1, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tuso.booking
    (id, resource_id, tenant_id, facility_id, booked_by, subject_ref,
     purpose, start_time, end_time, status, notes)
VALUES
    ('e1000000-0000-4000-8000-000000000001',
     'd1000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     1, 'CPID-ZW-00001', 'CPID-ZW-00001',
     'DIABETES_REVIEW',
     NOW() + INTERVAL '3 days 9 hours',
     NOW() + INTERVAL '3 days 9 hours 30 minutes',
     'CONFIRMED',
     'Follow-up for HbA1c results and medication adjustment'),

    ('e1000000-0000-4000-8000-000000000002',
     'd1000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000001',
     1, 'CPID-ZW-00001', 'CPID-ZW-00001',
     'GENERAL_CHECKUP',
     NOW() + INTERVAL '10 days 14 hours',
     NOW() + INTERVAL '10 days 14 hours 30 minutes',
     'CONFIRMED',
     'Routine annual health check'),

    ('e1000000-0000-4000-8000-000000000003',
     'd1000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     1, 'CPID-ZW-00001', 'CPID-ZW-00001',
     'DIABETES_REVIEW',
     NOW() - INTERVAL '14 days' + INTERVAL '10 hours',
     NOW() - INTERVAL '14 days' + INTERVAL '10 hours 30 minutes',
     'COMPLETED',
     'Initial diabetes assessment — referred for RBS and HbA1c'),

    ('e1000000-0000-4000-8000-000000000004',
     'd1000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     1, 'CPID-ZW-00001', 'CPID-ZW-00001',
     'PHARMACY_PICKUP',
     NOW() + INTERVAL '1 day 11 hours',
     NOW() + INTERVAL '1 day 11 hours 15 minutes',
     'CONFIRMED',
     'Metformin 500mg refill collection')
ON CONFLICT (id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. EXPERIENCE BFF — Patient index + consent + coverage + marketplace
-- ─────────────────────────────────────────────────────────────────────────────
\c experience_bff

INSERT INTO patients
    (id, tenant_id, cpid, given_name, family_name, date_of_birth, sex,
     phone, status)
VALUES
    ('b0000000-0000-4000-8000-000000000001',
     'tenant-moh-zw',
     'CPID-ZW-00001',
     'Tatenda', 'Moyo',
     '1990-03-15', 'MALE',
     '+263771234567', 'ACTIVE')
ON CONFLICT (tenant_id, cpid) DO NOTHING;

INSERT INTO consent_preferences
    (tenant_id, patient_id, category, description, granted, granted_at)
VALUES
    ('tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'DATA_SHARING',
     'Share my health data with authorised providers for treatment purposes.',
     true, NOW() - INTERVAL '30 days'),

    ('tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'RESEARCH',
     'Allow anonymised data to be used for public health research.',
     false, NULL),

    ('tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'MARKETING',
     'Receive personalised health tips and service offers from Impilo partners.',
     false, NULL),

    ('tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'EMERGENCY_ACCESS',
     'Allow emergency access to my records when I am incapacitated.',
     true, NOW() - INTERVAL '30 days')
ON CONFLICT (tenant_id, patient_id, category) DO NOTHING;

INSERT INTO coverage_plans
    (id, tenant_id, patient_id, plan_name, plan_type, member_id,
     status, effective_from, copay, deductible, out_of_pocket_max, currency)
VALUES
    ('f2000000-0000-4000-8000-000000000001',
     'tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'National Health Insurance Scheme — Public Tier',
     'GOVERNMENT',
     'NHIS-0000001-ZW',
     'ACTIVE',
     '2024-01-01',
     10.00, 0.00, 50000.00, 'ZWG'),

    ('f2000000-0000-4000-8000-000000000002',
     'tenant-moh-zw',
     'b0000000-0000-4000-8000-000000000001',
     'Community-Based Health Insurance — Rural',
     'GOVERNMENT',
     'CBHI-0000001-ZW',
     'ACTIVE',
     '2024-01-01',
     5.00, 0.00, 20000.00, 'ZWG')
ON CONFLICT (id) DO NOTHING;

INSERT INTO marketplace_services
    (id, tenant_id, name, description, category, facility_id,
     facility_name, price, currency, available)
VALUES
    ('c3000000-0000-4000-8000-000000000001',
     'tenant-moh-zw',
     'Diabetes Management Programme',
     'Structured 6-month programme with regular HbA1c monitoring, dietary counselling, and medication review.',
     'CHRONIC_DISEASE',
     'a0000000-0000-4000-8000-000000000001',
     'Harare Central Hospital',
     50.00, 'ZWG', true),

    ('c3000000-0000-4000-8000-000000000002',
     'tenant-moh-zw',
     'Hypertension Screening',
     'Blood pressure measurement, BMI check, and cardiovascular risk scoring.',
     'SCREENING',
     'a0000000-0000-4000-8000-000000000001',
     'Harare Central Hospital',
     0.00, 'ZWG', true),

    ('c3000000-0000-4000-8000-000000000003',
     'tenant-moh-zw',
     'HIV Voluntary Counselling & Testing',
     'Confidential HIV counselling and rapid test with same-day results.',
     'HIV',
     'a0000000-0000-4000-8000-000000000001',
     'Harare Central Hospital',
     0.00, 'ZWG', true)
ON CONFLICT (id) DO NOTHING;
