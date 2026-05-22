-- =============================================================================
-- VARAPI — Provider Registry Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Professional councils: MDPCZ, NMCZ, HPCZ, PHCZ, MOHCC_HQ
-- Providers: 6 clinicians (doctors, nurses, pharmacist) across 3 facilities
-- Idempotent: ON CONFLICT DO NOTHING; FK lookups use natural keys via subquery
-- =============================================================================

\c varapi

INSERT INTO varapi.councils
    (tenant_id, council_code, name, council_type, status, email, phone)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'MDPCZ', 'Medical and Dental Practitioners Council of Zimbabwe',
     'PROFESSIONAL_COUNCIL', 'ACTIVE',
     'registrar@mdpcz.org.zw', '+263242700756'),

    ('00000000-0000-4000-8000-000000000001',
     'NMCZ', 'Nursing and Midwifery Council of Zimbabwe',
     'PROFESSIONAL_COUNCIL', 'ACTIVE',
     'registrar@nmcz.org.zw', '+263242708235'),

    ('00000000-0000-4000-8000-000000000001',
     'PHCZ', 'Pharmaceutical Council of Zimbabwe',
     'PROFESSIONAL_COUNCIL', 'ACTIVE',
     'registrar@phcz.org.zw', '+263242700212'),

    ('00000000-0000-4000-8000-000000000001',
     'HPCZ', 'Health Professions Council of Zimbabwe',
     'PROFESSIONAL_COUNCIL', 'ACTIVE',
     'registrar@hpcz.org.zw', '+263242704551'),

    ('00000000-0000-4000-8000-000000000001',
     'MOHCC_HQ', 'Ministry of Health and Child Care Zimbabwe',
     'ORG_HR', 'ACTIVE',
     'hro@mohcc.gov.zw', '+263242700644')
ON CONFLICT (council_code) DO NOTHING;

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
     'Tendai', 'Mapfumo', 'MDPCZ-2015-001',
     'PROV-ZW-00001', 'Dr', '1980-04-12', 'MALE', 'ZW',
     'mapfumo@hch.gov.zw', '+263771000001', 'MEDICAL_DOCTOR', 'GENERAL_PRACTITIONER',
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000002',
     'Grace', 'Musekwa', 'NMCZ-2010-042',
     'PROV-ZW-00002', 'Sr', '1975-08-30', 'FEMALE', 'ZW',
     'musekwa@hch.gov.zw', '+263771000002', 'NURSE', 'REGISTERED_NURSE',
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000003',
     'Simba', 'Nyamukapa', 'MDPCZ-2008-019',
     'PROV-ZW-00003', 'Dr', '1973-12-05', 'MALE', 'ZW',
     'nyamukapa@pgh.gov.zw', '+263771000003', 'MEDICAL_DOCTOR', 'SURGEON',
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000004',
     'Faith', 'Zimba', 'PHCZ-2018-077',
     'PROV-ZW-00004', 'Ms', '1987-03-21', 'FEMALE', 'ZW',
     'zimba@hch.gov.zw', '+263771000004', 'PHARMACIST', 'PHARMACIST',
     (SELECT id FROM varapi.councils WHERE council_code = 'PHCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000005',
     'Blessing', 'Chiweshe', 'NMCZ-2012-091',
     'PROV-ZW-00005', 'Sr', '1982-06-14', 'FEMALE', 'ZW',
     'chiweshe@cch.gov.zw', '+263771000005', 'NURSE', 'CLINICAL_NURSE_SPECIALIST',
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000006', 'c0000000-0000-4000-8000-000000000006',
     'Tapiwa', 'Chigumba', 'MDPCZ-2020-112',
     'PROV-ZW-00006', 'Dr', '1992-09-18', 'MALE', 'ZW',
     'chigumba@mpilo.gov.zw', '+263771000006', 'MEDICAL_DOCTOR', 'GENERAL_PRACTITIONER',
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'b0000000-0000-4000-8000-000000000010', 'b0000000-0000-4000-8000-000000000010',
     'System', 'Admin', 'MOHCC-ADMIN-001',
     'PROV-ZW-ADMIN-001', 'Dr', '1985-01-01', 'MALE', 'ZW',
     'superadmin@impilo.gov.zw', '+263771000010', 'SYSTEM_ADMINISTRATOR', 'PLATFORM_ADMIN',
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE')
ON CONFLICT (provider_ref) DO NOTHING;

INSERT INTO varapi.provider_specialties
    (provider_id, tenant_id, specialty_code, specialty_name, primary_specialty, zibo_validated)
VALUES
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000001'),
     '00000000-0000-4000-8000-000000000001', 'GP',   'General Practice',      true,  true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000002'),
     '00000000-0000-4000-8000-000000000001', 'PCN',  'Primary Care Nursing',  true,  true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     '00000000-0000-4000-8000-000000000001', 'SURG', 'General Surgery',       true,  true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     '00000000-0000-4000-8000-000000000001', 'ORTH', 'Orthopaedics',          false, true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000004'),
     '00000000-0000-4000-8000-000000000001', 'CLIN', 'Clinical Pharmacy',     true,  true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000005'),
     '00000000-0000-4000-8000-000000000001', 'MHNS', 'Mental Health Nursing', true,  true),
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000006'),
     '00000000-0000-4000-8000-000000000001', 'GP',   'General Practice',      true,  true),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001', 'ADMIN', 'Platform Administration', true, true)
ON CONFLICT DO NOTHING;

INSERT INTO varapi.provider_council_affiliations
    (provider_id, council_id, tenant_id, registration_number, registration_date, status)
VALUES
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000001'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001', 'MDPCZ-2015-001', '2015-06-01', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000002'),
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'),
     '00000000-0000-4000-8000-000000000001', 'NMCZ-2010-042', '2010-03-15', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001', 'MDPCZ-2008-019', '2008-09-01', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000004'),
     (SELECT id FROM varapi.councils WHERE council_code = 'PHCZ'),
     '00000000-0000-4000-8000-000000000001', 'PHCZ-2018-077', '2018-01-20', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000005'),
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'),
     '00000000-0000-4000-8000-000000000001', 'NMCZ-2012-091', '2012-07-10', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000006'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001', 'MDPCZ-2020-112', '2020-02-28', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'),
     '00000000-0000-4000-8000-000000000001', 'MOHCC-ADMIN-001', '2020-01-01', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO varapi.licenses
    (provider_id, council_id, tenant_id, license_type, license_number,
     status, valid_from, valid_to, issued_by)
VALUES
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000001'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-MDPCZ-2024-001', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Medical and Dental Practitioners Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000002'),
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-NMCZ-2024-042', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Nursing and Midwifery Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-MDPCZ-2024-019', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Medical and Dental Practitioners Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001',
     'SPECIALIST', 'LIC-MDPCZ-SPEC-2024-019', 'ACTIVE', '2024-01-01', '2026-12-31',
     'Medical and Dental Practitioners Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000004'),
     (SELECT id FROM varapi.councils WHERE council_code = 'PHCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-PHCZ-2024-077', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Pharmaceutical Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000005'),
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-NMCZ-2024-091', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Nursing and Midwifery Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000006'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-MDPCZ-2024-112', 'ACTIVE', '2024-01-01', '2025-12-31',
     'Medical and Dental Practitioners Council of Zimbabwe'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'),
     '00000000-0000-4000-8000-000000000001',
     'PRACTICE', 'LIC-MOHCC-ADMIN-2024-001', 'ACTIVE', '2024-01-01', '2030-12-31',
     'Ministry of Health and Child Care Zimbabwe')
ON CONFLICT DO NOTHING;

INSERT INTO varapi.privileges
    (provider_id, tenant_id, facility_id, workspace_id, scope, privilege_type,
     granted_by, valid_from, valid_to, status)
VALUES
    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000001'),
     '00000000-0000-4000-8000-000000000001',
     1, 'a0000000-0000-4000-8000-000000000001',
     'CLINICAL', 'PRESCRIBING', 'admin.central', '2024-01-01', '2025-12-31', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     '00000000-0000-4000-8000-000000000001',
     2, 'a0000000-0000-4000-8000-000000000004',
     'CLINICAL', 'PRESCRIBING', 'admin.central', '2024-01-01', '2025-12-31', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000003'),
     '00000000-0000-4000-8000-000000000001',
     2, 'a0000000-0000-4000-8000-000000000004',
     'CLINICAL', 'SURGERY', 'admin.central', '2024-01-01', '2025-12-31', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'c0000000-0000-4000-8000-000000000006'),
     '00000000-0000-4000-8000-000000000001',
     4, 'a0000000-0000-4000-8000-000000000008',
     'CLINICAL', 'PRESCRIBING', 'admin.central', '2024-01-01', '2025-12-31', 'ACTIVE'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001',
     1, 'a0000000-0000-4000-8000-000000000001',
     'ADMINISTRATIVE', 'SYSTEM_ADMIN', 'platform.bootstrap', '2024-01-01', '2030-12-31', 'APPROVED'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001',
     2, 'a0000000-0000-4000-8000-000000000005',
     'ADMINISTRATIVE', 'SYSTEM_ADMIN', 'platform.bootstrap', '2024-01-01', '2030-12-31', 'APPROVED'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001',
     3, 'a0000000-0000-4000-8000-000000000006',
     'ADMINISTRATIVE', 'SYSTEM_ADMIN', 'platform.bootstrap', '2024-01-01', '2030-12-31', 'APPROVED'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001',
     4, 'a0000000-0000-4000-8000-000000000008',
     'ADMINISTRATIVE', 'SYSTEM_ADMIN', 'platform.bootstrap', '2024-01-01', '2030-12-31', 'APPROVED'),

    ((SELECT id FROM varapi.provider WHERE provider_ref = 'b0000000-0000-4000-8000-000000000010'),
     '00000000-0000-4000-8000-000000000001',
     5, 'a0000000-0000-4000-8000-000000000009',
     'ADMINISTRATIVE', 'SYSTEM_ADMIN', 'platform.bootstrap', '2024-01-01', '2030-12-31', 'APPROVED')
ON CONFLICT DO NOTHING;
