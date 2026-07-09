-- =============================================================================
-- Persona Truth Pack (VARAPI) — journey-completion wave personas
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- Provider rows for the named demo personas in tools/auth/impilo-realm.json
-- (anchors must match the realm user attribute health_id):
--   PROV-ZW-00008 clerk.dube      Nyasha Dube    HEALTH_RECORDS_CLERK  (MOHCC_HQ)
--   PROV-ZW-00009 dr.gwena        Rudo Gwena     SPECIALIST_PHYSICIAN  (MDPCZ, Parirenyatwa)
--   PROV-ZW-00010 rad.nkomo       Sipho Nkomo    RADIOGRAPHER          (HPCZ)
--   PROV-ZW-00011 trainer.chikafu Fungai Chikafu HEALTH_EDUCATOR       (MOHCC_HQ)
--   PROV-ZW-00012 learner.tembo   Kudzai Tembo   ENROLLED_NURSE        (NMCZ)
-- pharm.zimba reuses the existing PROV-ZW-00004 (Faith Zimba, seed 04).
-- regulator.hpcz and iatg.gono are governance identities — no provider rows.
--
-- Idempotent via WHERE NOT EXISTS on the public id (mirrors seed 12).
-- =============================================================================

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000008', 'c0000000-0000-4000-8000-000000000008',
    'Nyasha', 'Dube', 'MOHCC-HR-2019-208',
    'PROV-ZW-00008', 'Ms', '1993-05-11', 'FEMALE', 'ZW',
    'nyasha.dube@mohcc.gov.zw', '+263771000008', 'HEALTH_RECORDS_OFFICER', 'HEALTH_RECORDS_CLERK',
    (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00008'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000009', 'c0000000-0000-4000-8000-000000000009',
    'Rudo', 'Gwena', 'MDPCZ-2011-064',
    'PROV-ZW-00009', 'Dr', '1979-11-02', 'FEMALE', 'ZW',
    'rudo.gwena@mohcc.gov.zw', '+263771000009', 'MEDICAL_DOCTOR', 'SPECIALIST_PHYSICIAN',
    (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00009'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000010', 'c0000000-0000-4000-8000-000000000010',
    'Sipho', 'Nkomo', 'HPCZ-2017-233',
    'PROV-ZW-00010', 'Mr', '1990-07-25', 'MALE', 'ZW',
    'sipho.nkomo@mohcc.gov.zw', '+263771000011', 'ALLIED_HEALTH_PRACTITIONER', 'RADIOGRAPHER',
    (SELECT id FROM varapi.councils WHERE council_code = 'HPCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00010'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000011', 'c0000000-0000-4000-8000-000000000011',
    'Fungai', 'Chikafu', 'MOHCC-EDU-2014-041',
    'PROV-ZW-00011', 'Mr', '1984-01-30', 'MALE', 'ZW',
    'fungai.chikafu@mohcc.gov.zw', '+263771000012', 'HEALTH_EDUCATION', 'HEALTH_EDUCATOR',
    (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00011'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000012', 'c0000000-0000-4000-8000-000000000012',
    'Kudzai', 'Tembo', 'NMCZ-2023-310',
    'PROV-ZW-00012', 'Sr', '1998-09-08', 'FEMALE', 'ZW',
    'kudzai.tembo@mohcc.gov.zw', '+263771000013', 'NURSE', 'ENROLLED_NURSE',
    (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00012'
);
