-- =============================================================================
-- COSTA V023 — HEALTH_WORKER exemption category (provider-as-patient perks)
--
-- Recognised health workers (VARAPI licensed providers / Vashandi workforce)
-- may receive a governed fee discount when THEY seek services. This is an
-- administrative/financial advantage only — never clinical-triage priority.
--
-- Governance: the example rule below is seeded DISABLED. A tenant must
-- explicitly flip it to ACTIVE (and set costa.recognition.enabled=true for
-- encounter enrichment) before any discount can apply.
-- =============================================================================

-- Re-add the category CHECK with HEALTH_WORKER included (V001 inline CHECK).
ALTER TABLE costa_exemption_rules
    DROP CONSTRAINT IF EXISTS costa_exemption_rules_category_check;

ALTER TABLE costa_exemption_rules
    ADD CONSTRAINT costa_exemption_rules_category_check
    CHECK (category IN ('CHILD','ELDERLY','MATERNITY','INDIGENT','PROGRAM_FUNDED',
                        'STAFF','CHRONIC','VETERAN','DISABILITY','HEALTH_WORKER','OTHER'));

-- Governed example rule: 50% discount capped at 200.00 per bill, DISABLED by
-- default. Idempotent (skip when a HEALTH_WORKER rule already exists for the
-- default tenant).
INSERT INTO costa_exemption_rules (
    tenant_id, name, category, version, rules, discount_pct, max_amount,
    effective_from, effective_to, status
)
SELECT
    '00000000-0000-4000-8000-000000000001'::uuid,
    'Recognised health worker fee discount (governed example — enable per tenant policy)',
    'HEALTH_WORKER',
    1,
    '{"source":"provider-perks-lane-c","recognition_classes":["LICENSED_PROVIDER","HEALTH_WORKFORCE","BOTH"],"doctrine":"administrative_financial_only"}'::jsonb,
    50.00,
    200.00,
    CURRENT_DATE,
    NULL,
    'DISABLED'
WHERE NOT EXISTS (
    SELECT 1 FROM costa_exemption_rules
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND category = 'HEALTH_WORKER'
);
