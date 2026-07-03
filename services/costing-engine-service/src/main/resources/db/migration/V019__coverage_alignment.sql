-- Coverage-in-billing (Scenario B/D): bill-level coverage snapshot + insurance
-- plans mirroring the live coverage-service plans so the existing
-- ExemptionEngine/recomputePartySplit machinery (keyed on
-- encounter.insurance_plan_id) finally has real rows to split against.

ALTER TABLE costa_bill_headers ADD COLUMN IF NOT EXISTS coverage_plan_code VARCHAR(64);
ALTER TABLE costa_bill_headers ADD COLUMN IF NOT EXISTS coverage_member_id UUID;
ALTER TABLE costa_bill_headers ADD COLUMN IF NOT EXISTS coverage_status VARCHAR(64);
ALTER TABLE costa_bill_headers ADD COLUMN IF NOT EXISTS coverage_checked_at TIMESTAMPTZ;

-- Plans mirror coverage-service seeds (COV-MOHCC-CORE, COV-PRIVATE-PLUS).
INSERT INTO costa_insurance_plans
    (tenant_id, insurer_name, plan_code, plan_name, coverage_pct, deductible, copay_pct, effective_from, status, created_at)
SELECT '00000000-0000-4000-8000-000000000001'::uuid,
       'MoHCC National Scheme', 'COV-MOHCC-CORE', 'MoHCC Core Cover',
       90.00, 0, 10.00, CURRENT_DATE, 'ACTIVE', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM costa_insurance_plans
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid AND plan_code = 'COV-MOHCC-CORE');

INSERT INTO costa_insurance_plans
    (tenant_id, insurer_name, plan_code, plan_name, coverage_pct, deductible, copay_pct, effective_from, status, created_at)
SELECT '00000000-0000-4000-8000-000000000001'::uuid,
       'Private Plus Medical Aid', 'COV-PRIVATE-PLUS', 'Private Plus',
       80.00, 10.00, 20.00, CURRENT_DATE, 'ACTIVE', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM costa_insurance_plans
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid AND plan_code = 'COV-PRIVATE-PLUS');
