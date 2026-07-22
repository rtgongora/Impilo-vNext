-- =====================================================================================
-- varapi :: V028 — Council ↔ org-registry link + the nine canonical councils (ROM-W1)
-- Ownership ruling R1: org-registry owns organisation IDENTITY; varapi.councils is the
-- professional-regulation PROFILE, bound by a unique FK to the org-registry organisation.
--
-- Reconciliation (PO decision "full rename to canonical" + governed-tenant move): five rows
-- were bootstrapped ad-hoc under the stray tenant 00000000-0000-4000-8000-000000000001 with
-- divergent codes (NMCZ, PHCZ, HPCZ). Rename them to canonical, re-tenant into the governed
-- tenant 00000000-0000-0000-0000-000000000001 (where the 4,241 providers + 304 policy rules
-- live), link to org-registry, and add the five missing councils. Idempotent on council_code.
-- MOHCC_HQ (ORG_HR) is not a ROM council and is left untouched.
-- =====================================================================================

ALTER TABLE varapi.councils
    ADD COLUMN IF NOT EXISTS org_registry_org_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_councils_org_registry_org
    ON varapi.councils (org_registry_org_id) WHERE org_registry_org_id IS NOT NULL;

COMMENT ON COLUMN varapi.councils.org_registry_org_id IS
    'FK to org_registry.org_registry_organization.id — organisation identity SoR (ROM R1).';

-- 1. Rename the three divergent legacy codes to canonical (no deletes). The subsequent
--    upsert then re-tenants, links and normalises every row.
UPDATE varapi.councils SET council_code = 'NCZ' WHERE council_code = 'NMCZ';
UPDATE varapi.councils SET council_code = 'PCZ' WHERE council_code = 'PHCZ';
UPDATE varapi.councils SET council_code = 'HPA', council_type = 'HPA' WHERE council_code = 'HPCZ';

-- 2. Upsert all nine canonical councils: re-tenant to the governed tenant, set canonical name
--    + type, link the org-registry UUID, seed the registration-number pattern (TO_CONFIRM
--    placeholders per docs/design/regulatory-operating-model/orgs/*.md).
INSERT INTO varapi.councils
    (tenant_id, council_code, name, council_type, org_registry_org_id, registration_number_pattern, status) VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'HPA',    'Health Professions Authority',                                   'HPA',                'a5000000-0000-4000-8000-000000000001'::uuid, NULL,                     'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'MDPCZ',  'Medical and Dental Practitioners Council of Zimbabwe',            'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000002'::uuid, '^[A-Z]{1,3}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'NCZ',    'Nurses Council of Zimbabwe',                                     'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000003'::uuid, '^[A-Z]{1,3}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'PCZ',    'Pharmacists Council of Zimbabwe',                                'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000004'::uuid, '^[A-Z]{1,3}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'AHPCZ',  'Allied Health Practitioners Council of Zimbabwe',                'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000005'::uuid, '^[A-Z]{1,4}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'EHPCZ',  'Environmental Health Practitioners Council of Zimbabwe',          'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000006'::uuid, '^[A-Z]{1,4}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'MRPCZ',  'Medical Rehabilitation Practitioners Council of Zimbabwe',        'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000007'::uuid, '^[A-Z]{1,4}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'MLCSCZ', 'Medical Laboratory and Clinical Scientists Council of Zimbabwe',  'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000008'::uuid, '^[A-Z]{1,4}[0-9]{3,6}$', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'NTCZ',   'Natural Therapists Council of Zimbabwe',                         'PROFESSIONAL_COUNCIL','a5000000-0000-4000-8000-000000000009'::uuid, '^[A-Z]{1,4}[0-9]{3,6}$', 'ACTIVE')
ON CONFLICT (council_code) DO UPDATE SET
    tenant_id                   = EXCLUDED.tenant_id,
    name                        = EXCLUDED.name,
    council_type                = EXCLUDED.council_type,
    org_registry_org_id         = EXCLUDED.org_registry_org_id,
    registration_number_pattern = COALESCE(varapi.councils.registration_number_pattern, EXCLUDED.registration_number_pattern),
    status                      = 'ACTIVE',
    updated_at                  = now();

-- 3. Seed a regulatory-config profile per council (idempotent per tenant+council). Fee amounts
--    stay governed/PENDING (never invented here); workflow template is the shared standard.
INSERT INTO varapi.council_regulatory_configs
    (tenant_id, council_id, workflow_template_code, cpd_rules_json, fees_json, notes)
SELECT c.tenant_id, c.id,
       CASE WHEN c.council_code = 'HPA' THEN 'HPA_FACILITY_DEFAULT' ELSE 'COUNCIL_STANDARD_V1' END,
       '{"required_units": null, "status": "TO_CONFIRM"}'::jsonb,
       '{"schedule_ref": null, "status": "PENDING_REGULATOR_APPROVAL"}'::jsonb,
       'ROM-W1 seed; parameters TO_CONFIRM per docs/design/regulatory-operating-model/orgs/'
FROM varapi.councils c
WHERE c.org_registry_org_id IS NOT NULL
  AND c.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT (tenant_id, council_id) DO NOTHING;
