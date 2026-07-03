-- =============================================================================
-- Scenario persona alignment (VARAPI) — nurse.chienda (Rumbidzai Chienda)
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
--
-- The Keycloak persona nurse.chienda had no VARAPI provider row, so the
-- linked-ids → provider → workforce-assignment chain broke at the first hop
-- and she could never gain work access. Registers her as PROV-ZW-00007
-- (Registered Nurse), person anchor c0000000-0000-4000-8000-000000000007 —
-- must match the realm user attribute health_id in tools/auth/impilo-realm.json.
--
-- Idempotent via WHERE NOT EXISTS on the public id.
-- =============================================================================

INSERT INTO varapi.provider
    (tenant_id, provider_ref, impilo_health_id, given_name, family_name, practice_number,
     provider_public_id, title, date_of_birth, gender, nationality,
     email, phone, profession, cadre, primary_council_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'c0000000-0000-4000-8000-000000000007', 'c0000000-0000-4000-8000-000000000007',
    'Rumbidzai', 'Chienda', 'NMCZ-2016-118',
    'PROV-ZW-00007', 'Sr', '1988-02-17', 'FEMALE', 'ZW',
    'chienda@mohcc.gov.zw', '+263771000007', 'NURSE', 'REGISTERED_NURSE',
    (SELECT id FROM varapi.councils WHERE council_code = 'NMCZ'), 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM varapi.provider WHERE provider_public_id = 'PROV-ZW-00007'
);
