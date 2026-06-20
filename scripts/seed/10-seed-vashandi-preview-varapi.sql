-- =============================================================================
-- Vashandi preview personas — VARAPI provider records
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Idempotent via provider_ref ON CONFLICT DO NOTHING
-- =============================================================================

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000101', 'c0000000-0000-4000-8000-000000000101',
     'Tariro', 'Mhlanga', 'MOHCC-VASH-001',
     'PROV-ZW-VASH-001', 'Mr', '1978-02-11', 'MALE', 'ZW',
     'vashandi.national@mohcc.gov.zw', '+263771000101', 'WORKFORCE_ADMINISTRATOR', 'NATIONAL_WORKFORCE_ADMIN',
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000102', 'c0000000-0000-4000-8000-000000000102',
     'Rutendo', 'Chidza', 'MOHCC-VASH-002',
     'PROV-ZW-VASH-002', 'Ms', '1984-09-03', 'FEMALE', 'ZW',
     'vashandi.facility@mohcc.gov.zw', '+263771000102', 'WORKFORCE_ADMINISTRATOR', 'FACILITY_WORKFORCE_MANAGER',
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000103', 'c0000000-0000-4000-8000-000000000103',
     'Farai', 'Dube', 'MOHCC-VASH-003',
     'PROV-ZW-VASH-003', 'Mr', '1993-12-20', 'MALE', 'ZW',
     'vashandi.worker@mohcc.gov.zw', '+263771000103', 'NURSE', 'REGISTERED_NURSE',
     (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000104', 'c0000000-0000-4000-8000-000000000104',
     'Chipo', 'Moyo', 'MOHCC-VASH-004',
     'PROV-ZW-VASH-004', 'Ms', '1981-05-17', 'FEMALE', 'ZW',
     'vashandi.hsc@mohcc.gov.zw', '+263771000104', 'WORKFORCE_ADMINISTRATOR', 'HSC_WORKFORCE_OFFICER',
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'),

    ('00000000-0000-4000-8000-000000000001',
     'c0000000-0000-4000-8000-000000000105', 'c0000000-0000-4000-8000-000000000105',
     'Brian', 'Mutasa', 'MOHCC-VASH-005',
     'PROV-ZW-VASH-005', 'Mr', '1976-08-08', 'MALE', 'ZW',
     'vashandi.reviewer@mohcc.gov.zw', '+263771000105', 'WORKFORCE_ADMINISTRATOR', 'WORKFORCE_ACCESS_REVIEWER',
     (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE')
ON CONFLICT (provider_ref) DO NOTHING;
