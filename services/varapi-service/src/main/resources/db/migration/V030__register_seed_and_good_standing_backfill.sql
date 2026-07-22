-- =====================================================================================
-- varapi :: V030 — Register catalogue seed + good-standing backfill (ROM-W3)
-- Seeds the statutory registers per council (placeholders per docs/design/regulatory-operating-
-- model/orgs/*.md — register names remain TO_CONFIRM with the registrars) and derives first-class
-- good-standing rows from the existing string status. Idempotent. Governed tenant only.
-- =====================================================================================

-- 1. Register catalogue. council_id is resolved from the canonical council_code (V028).
INSERT INTO varapi.professional_registers (tenant_id, council_id, register_code, name)
SELECT c.tenant_id, c.id, v.register_code, v.name
FROM (VALUES
    ('MDPCZ',  'MDPCZ_MEDICAL',            'Register of Medical Practitioners'),
    ('MDPCZ',  'MDPCZ_DENTAL',             'Register of Dental Practitioners'),
    ('MDPCZ',  'MDPCZ_SPECIALIST',         'Specialist Register'),
    ('MDPCZ',  'MDPCZ_PROVISIONAL',        'Provisional Register'),
    ('NCZ',    'NCZ_GENERAL',              'Register of General Nurses'),
    ('NCZ',    'NCZ_MIDWIFE',              'Register of Midwives'),
    ('NCZ',    'NCZ_MENTAL_HEALTH',        'Register of Mental Health Nurses'),
    ('NCZ',    'NCZ_SPECIALIST',           'Specialist Nursing Register'),
    ('PCZ',    'PCZ_PHARMACIST',           'Register of Pharmacists'),
    ('PCZ',    'PCZ_TECHNICIAN',           'Register of Pharmacy Technicians'),
    ('AHPCZ',  'AHPCZ_GENERAL',            'Allied Health Practitioners Register (per-profession TO_CONFIRM)'),
    ('EHPCZ',  'EHPCZ_OFFICER',            'Register of Environmental Health Officers'),
    ('EHPCZ',  'EHPCZ_TECHNICIAN',         'Register of Environmental Health Technicians'),
    ('MRPCZ',  'MRPCZ_PHYSIOTHERAPIST',    'Register of Physiotherapists'),
    ('MRPCZ',  'MRPCZ_OCCUPATIONAL_THERAPIST', 'Register of Occupational Therapists'),
    ('MRPCZ',  'MRPCZ_REHAB_TECHNICIAN',   'Register of Rehabilitation Technicians'),
    ('MLCSCZ', 'MLCSCZ_LAB_SCIENTIST',     'Register of Medical Laboratory Scientists'),
    ('MLCSCZ', 'MLCSCZ_LAB_TECHNICIAN',    'Register of Medical Laboratory Technicians'),
    ('MLCSCZ', 'MLCSCZ_CLINICAL_SCIENTIST','Register of Clinical Scientists'),
    ('NTCZ',   'NTCZ_GENERAL',             'Natural Therapists Register (per-modality TO_CONFIRM)')
) AS v(council_code, register_code, name)
JOIN varapi.councils c
  ON c.council_code = v.council_code
 AND c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT (council_id, register_code) DO NOTHING;

-- 2. Derive first-class good standing from the existing registration-record status. A provider
--    with any SUSPENDED/REVOKED record at a council is NOT_GOOD; otherwise GOOD. Idempotent.
INSERT INTO varapi.provider_good_standing (tenant_id, provider_id, council_id, standing, derivation)
SELECT r.tenant_id, r.provider_id, r.council_id,
       CASE WHEN bool_or(r.status IN ('SUSPENDED', 'REVOKED', 'REMOVED')) THEN 'NOT_GOOD' ELSE 'GOOD' END,
       'ROM-W3 backfill from provider_council_registration_records.status'
FROM varapi.provider_council_registration_records r
WHERE r.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
GROUP BY r.tenant_id, r.provider_id, r.council_id
ON CONFLICT (tenant_id, provider_id, council_id) DO NOTHING;
