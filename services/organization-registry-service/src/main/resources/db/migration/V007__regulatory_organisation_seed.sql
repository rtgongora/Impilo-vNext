-- =====================================================================================
-- org-registry :: V007 — The nine regulatory organisations (ROM-W1)
-- HPA + the eight professional councils, seeded with deterministic UUIDs under the governed
-- tenant (00000000-0000-0000-0000-000000000001 — where the 4,241 varapi providers and all 304
-- tshepo-authz policy rules live). Identity SoR (ownership ruling R1). varapi.councils rows
-- cross-link to these UUIDs (varapi V028). Idempotent on the unique org code.
-- Deterministic UUIDs: a5000000-0000-4000-8000-00000000000N (N = 1 HPA … 9 NTCZ).
-- =====================================================================================

INSERT INTO org_registry.org_registry_organization
    (id, tenant_id, code, legal_name, org_type, status, verification_status, source) VALUES
    ('a5000000-0000-4000-8000-000000000001'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'HPA',    'Health Professions Authority',                                      'PUBLIC_HEALTH_AUTHORITY', 'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000002'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'MDPCZ',  'Medical and Dental Practitioners Council of Zimbabwe',              'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000003'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'NCZ',    'Nurses Council of Zimbabwe',                                        'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000004'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'PCZ',    'Pharmacists Council of Zimbabwe',                                   'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000005'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'AHPCZ',  'Allied Health Practitioners Council of Zimbabwe',                   'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000006'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'EHPCZ',  'Environmental Health Practitioners Council of Zimbabwe',            'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000007'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'MRPCZ',  'Medical Rehabilitation Practitioners Council of Zimbabwe',          'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000008'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'MLCSCZ', 'Medical Laboratory and Clinical Scientists Council of Zimbabwe',    'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE'),
    ('a5000000-0000-4000-8000-000000000009'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'NTCZ',   'Natural Therapists Council of Zimbabwe',                            'PROFESSIONAL_COUNCIL',    'ACTIVE', 'VERIFIED', 'NATIVE')
ON CONFLICT (code) DO UPDATE SET
    legal_name          = EXCLUDED.legal_name,
    org_type            = EXCLUDED.org_type,
    tenant_id           = EXCLUDED.tenant_id,
    status              = EXCLUDED.status,
    verification_status = EXCLUDED.verification_status,
    updated_at          = NOW();
