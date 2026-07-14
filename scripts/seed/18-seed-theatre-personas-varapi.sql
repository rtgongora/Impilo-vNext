-- =============================================================================
-- Theatre Persona Truth Pack (VARAPI) — operating-theatre product-truth recovery (Wave 0)
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- The perioperative episode (inpatient.procedure_episode + TheatreController) is
-- already built, but a fresh estate has NO surgical persona and NO theatre room,
-- so the elective happy path hard-blocks on readiness. This pack seeds the
-- surgical team as VARAPI providers (anchors must match the realm user attribute
-- health_id in tools/auth/impilo-realm.json), the surgeon's SURGERY privilege
-- (the TEAM readiness gate + operative-note sign check a surgical scope via
-- VARAPI), the anaesthetist's ANAESTHESIA privilege, and their specialties.
--
--   PROV-ZW-00020 surgeon.moyo      Tapiwa Moyo      MEDICAL_DOCTOR/SURGEON        (MDPCZ)
--   PROV-ZW-00021 anaes.ndlovu      Chiedza Ndlovu   MEDICAL_DOCTOR/ANAESTHETIST   (MDPCZ)
--   PROV-ZW-00022 scrub.ncube       Tendai Ncube     NURSE/SCRUB_NURSE             (NMCZ)
--   PROV-ZW-00023 pacu.sibanda      Ruvimbo Sibanda  NURSE/RECOVERY_NURSE          (NMCZ)
--   PROV-ZW-00024 ward.dube         Farai Dube       NURSE/REGISTERED_NURSE        (NMCZ)
--   PROV-ZW-00025 porter.chuma      Blessing Chuma   SUPPORT_STAFF/PORTER          (MOHCC_HQ)
--   PROV-ZW-00026 coord.mutasa      Netsai Mutasa    HEALTH_ADMINISTRATION/THEATRE_COORDINATOR (MOHCC_HQ)
--
-- health_id / provider_ref range c0...000000030-00036 (00001-00020 are taken).
-- provider_public_id range PROV-ZW-00020-00026 (00001-00014 taken by seeds 04/12/14).
-- Idempotent via WHERE NOT EXISTS on the public id / (provider,scope,facility) /
-- (provider,specialty) — mirrors seeds 04 / 12 / 14.
-- =============================================================================

-- ── Surgical team providers ──────────────────────────────────────────────────
INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000030', 'c0000000-0000-4000-8000-000000000030',
    'Tapiwa', 'Moyo', 'MDPCZ-2009-517',
    'PROV-ZW-00020', 'Dr', '1976-04-12', 'MALE', 'ZW',
    'tapiwa.moyo@mohcc.gov.zw', '+263771000030', 'MEDICAL_DOCTOR', 'SURGEON',
    (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00020'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000031', 'c0000000-0000-4000-8000-000000000031',
    'Chiedza', 'Ndlovu', 'MDPCZ-2012-244',
    'PROV-ZW-00021', 'Dr', '1981-08-30', 'FEMALE', 'ZW',
    'chiedza.ndlovu@mohcc.gov.zw', '+263771000031', 'MEDICAL_DOCTOR', 'ANAESTHETIST',
    (SELECT id FROM varapi.councils WHERE council_code = 'MDPCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00021'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000032', 'c0000000-0000-4000-8000-000000000032',
    'Tendai', 'Ncube', 'NMCZ-2015-882',
    'PROV-ZW-00022', 'Sr', '1988-02-19', 'FEMALE', 'ZW',
    'tendai.ncube@mohcc.gov.zw', '+263771000032', 'NURSE', 'SCRUB_NURSE',
    (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00022'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000033', 'c0000000-0000-4000-8000-000000000033',
    'Ruvimbo', 'Sibanda', 'NMCZ-2016-913',
    'PROV-ZW-00023', 'Sr', '1990-11-05', 'FEMALE', 'ZW',
    'ruvimbo.sibanda@mohcc.gov.zw', '+263771000033', 'NURSE', 'RECOVERY_NURSE',
    (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00023'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000034', 'c0000000-0000-4000-8000-000000000034',
    'Farai', 'Dube', 'NMCZ-2018-455',
    'PROV-ZW-00024', 'Mr', '1992-06-27', 'MALE', 'ZW',
    'farai.dube@mohcc.gov.zw', '+263771000034', 'NURSE', 'REGISTERED_NURSE',
    (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00024'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000035', 'c0000000-0000-4000-8000-000000000035',
    'Blessing', 'Chuma', 'MOHCC-OPS-2019-771',
    'PROV-ZW-00025', 'Mr', '1994-09-14', 'MALE', 'ZW',
    'blessing.chuma@mohcc.gov.zw', '+263771000035', 'SUPPORT_STAFF', 'PORTER',
    (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00025'
);

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000036', 'c0000000-0000-4000-8000-000000000036',
    'Netsai', 'Mutasa', 'MOHCC-ADM-2017-330',
    'PROV-ZW-00026', 'Mrs', '1985-12-02', 'FEMALE', 'ZW',
    'netsai.mutasa@mohcc.gov.zw', '+263771000036', 'HEALTH_ADMINISTRATION', 'THEATRE_COORDINATOR',
    (SELECT id FROM varapi.councils WHERE council_code = 'MOHCC_HQ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00026'
);

-- ── Surgeon SURGERY privilege (facilities 1 + 2) ─────────────────────────────
-- The TheatreReadinessClient surgeon-scope check and the operative-note sign
-- gate both call VARAPI GET /v1/internal/providers/{publicId}/privileges and look
-- for an active surgical privilege. scope=SURGERY is the domain-correct surgical
-- scope (V002 comments: GENERAL_PRACTICE, SURGERY, PRESCRIBE). status=APPROVED is
-- the VARAPI privilege lifecycle's granted state.
INSERT INTO varapi.privileges
    (provider_id, tenant_id, facility_id, scope, privilege_type, status,
     granted_by, granted_at, valid_from)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00020'),
    '00000000-0000-4000-8000-000000000001', 1, 'SURGERY', 'CLINICAL', 'APPROVED',
    'seed-theatre', now(), CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.privileges p
    JOIN varapi.provider pr ON pr.id = p.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00020' AND p.scope = 'SURGERY' AND p.facility_id = 1
);

INSERT INTO varapi.privileges
    (provider_id, tenant_id, facility_id, scope, privilege_type, status,
     granted_by, granted_at, valid_from)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00020'),
    '00000000-0000-4000-8000-000000000001', 2, 'SURGERY', 'CLINICAL', 'APPROVED',
    'seed-theatre', now(), CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.privileges p
    JOIN varapi.provider pr ON pr.id = p.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00020' AND p.scope = 'SURGERY' AND p.facility_id = 2
);

-- ── Anaesthetist ANAESTHESIA privilege (facilities 1 + 2) ────────────────────
INSERT INTO varapi.privileges
    (provider_id, tenant_id, facility_id, scope, privilege_type, status,
     granted_by, granted_at, valid_from)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00021'),
    '00000000-0000-4000-8000-000000000001', 1, 'ANAESTHESIA', 'CLINICAL', 'APPROVED',
    'seed-theatre', now(), CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.privileges p
    JOIN varapi.provider pr ON pr.id = p.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00021' AND p.scope = 'ANAESTHESIA' AND p.facility_id = 1
);

INSERT INTO varapi.privileges
    (provider_id, tenant_id, facility_id, scope, privilege_type, status,
     granted_by, granted_at, valid_from)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00021'),
    '00000000-0000-4000-8000-000000000001', 2, 'ANAESTHESIA', 'CLINICAL', 'APPROVED',
    'seed-theatre', now(), CURRENT_DATE
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.privileges p
    JOIN varapi.provider pr ON pr.id = p.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00021' AND p.scope = 'ANAESTHESIA' AND p.facility_id = 2
);

-- ── Specialties for surgeon + anaesthetist ───────────────────────────────────
INSERT INTO varapi.provider_specialties
    (provider_id, tenant_id, specialty_code, specialty_name, primary_specialty, zibo_validated)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00020'),
    '00000000-0000-4000-8000-000000000001', 'SURGERY', 'General Surgery', true, true
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider_specialties s
    JOIN varapi.provider pr ON pr.id = s.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00020' AND s.specialty_code = 'SURGERY'
);

INSERT INTO varapi.provider_specialties
    (provider_id, tenant_id, specialty_code, specialty_name, primary_specialty, zibo_validated)
SELECT
    (SELECT id FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00021'),
    '00000000-0000-4000-8000-000000000001', 'ANAESTHESIA', 'Anaesthesiology', true, true
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider_specialties s
    JOIN varapi.provider pr ON pr.id = s.provider_id
    WHERE pr.provider_public_id = 'PROV-ZW-00021' AND s.specialty_code = 'ANAESTHESIA'
);
