-- =============================================
-- Service : coverage-service (Ruvimbo)
-- Flyway  : V020__payer_formulary.sql
--
-- OF-B8 second half (Vol II §8.1.5/§10 — OF-G14/R62): the PAYER formulary layer
-- of the three-layer stance (ZIBO national medicine registry -> coverage
-- cv_formulary -> pharmacy rx_formulary). A formulary row answers, per plan
-- version: is THIS medication (ATC-coded against the ZIBO registry) covered,
-- at what tier/copay class, under WHICH benefit (the benefit-code mapping this
-- table exists for), and does the payer require prior authorisation for it.
--
-- Keying follows cv_benefit_definitions exactly: plan_version_id is the payer/
-- plan anchor (payer_ref/coverage_plan_ref reach it via cv_plan_versions ->
-- cv_products -> cv_schemes -> cv_payers). Additive only — no engine replaced.
-- =============================================

CREATE TABLE cv_formulary (
    id                     UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id              UUID            NOT NULL,
    pod_id                 VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    plan_version_id        UUID            NOT NULL REFERENCES cv_plan_versions(id),
    medication_code        VARCHAR(64)     NOT NULL,
    coding_system          VARCHAR(255)    NOT NULL DEFAULT 'http://www.whocc.no/atc',
    -- The benefit-code mapping: which plan benefit prices this medication.
    benefit_code           VARCHAR(64)     NOT NULL,
    covered                BOOLEAN         NOT NULL DEFAULT TRUE,
    tier                   VARCHAR(24)     NOT NULL DEFAULT 'STANDARD',  -- GENERIC, PREFERRED, STANDARD, SPECIALTY, CONTROLLED
    copay_class            VARCHAR(24),
    requires_authorisation BOOLEAN         NOT NULL DEFAULT FALSE,
    effective_from         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    effective_to           TIMESTAMPTZ,                                  -- NULL = open-ended
    notes                  VARCHAR(255),
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_formulary_entry UNIQUE (plan_version_id, medication_code, coding_system, effective_from),
    CONSTRAINT ck_cv_formulary_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);
CREATE INDEX idx_cv_formulary_lookup ON cv_formulary (tenant_id, plan_version_id, medication_code);
CREATE INDEX idx_cv_formulary_pv ON cv_formulary (plan_version_id);

-- Liability estimates record the medication resolution they were computed
-- under (§10.5 assumptions contract — auditable, additive).
ALTER TABLE cv_liability_estimates ADD COLUMN medication_code VARCHAR(64);
ALTER TABLE cv_liability_estimates ADD COLUMN coding_system   VARCHAR(255);
ALTER TABLE cv_liability_estimates ADD COLUMN formulary_ref   UUID;
ALTER TABLE cv_liability_estimates ADD COLUMN formulary_tier  VARCHAR(24);

-- ============================================================================
-- SEED — golden-tenant formulary rows over the V015 governed plans, mapping
-- ZIBO V007 starter medicines to plan benefits. Deterministic UUIDs (d6…).
-- An ACUTE_MEDS benefit is added (MEDICINE category) so acute medications map
-- to an honest benefit instead of overloading CHRONIC_MEDS.
-- ============================================================================
DO $$
DECLARE
    t_id UUID := '00000000-0000-4000-8000-000000000001';
    pv_mohcc   UUID := 'd4000000-0000-4000-8000-000000000001';  -- MOHCC National Core v1
    pv_private UUID := 'd4000000-0000-4000-8000-000000000002';  -- Private Medical Aid Plus v1
BEGIN
    -- Acute-medicine benefit on both governed plans (additive; CHRONIC_MEDS exists from V015).
    INSERT INTO cv_benefit_definitions (id, tenant_id, plan_version_id, benefit_code, category,
        description, coverage_percent, copay_amount, requires_authorisation, requires_referral, network_requirement)
    VALUES
      ('d5000000-0001-4000-8000-000000000006', t_id, pv_mohcc,
       'ACUTE_MEDS', 'MEDICINE', 'Acute and episodic medicines', 100.00, 0.00, FALSE, FALSE, 'ANY'),
      ('d5000000-0002-4000-8000-000000000006', t_id, pv_private,
       'ACUTE_MEDS', 'MEDICINE', 'Acute and episodic medicines', 80.00, 2.00, FALSE, FALSE, 'ANY')
    ON CONFLICT (plan_version_id, benefit_code) DO NOTHING;

    INSERT INTO cv_benefit_limits (tenant_id, benefit_id, limit_type, limit_value, limit_period)
    SELECT t_id, id, 'MONETARY', 1000.00, 'CALENDAR_YEAR'
    FROM cv_benefit_definitions
    WHERE tenant_id = t_id AND benefit_code = 'ACUTE_MEDS'
      AND plan_version_id IN (pv_mohcc, pv_private)
      AND NOT EXISTS (SELECT 1 FROM cv_benefit_limits l WHERE l.benefit_id = cv_benefit_definitions.id)
    ON CONFLICT DO NOTHING;

    -- Private Plus formulary: 10 ZIBO V007 starter medicines; morphine +
    -- ceftriaxone require prior authorisation; diazepam listed NOT covered
    -- (an honest exclusion row, distinct from silence).
    INSERT INTO cv_formulary (id, tenant_id, plan_version_id, medication_code, coding_system,
        benefit_code, covered, tier, copay_class, requires_authorisation, effective_from, notes)
    VALUES
      ('d6000000-0002-4000-8000-000000000001', t_id, pv_private, 'J01CA04', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE,  'GENERIC',    'A', FALSE, NOW(), 'Amoxicillin 500 mg capsule'),
      ('d6000000-0002-4000-8000-000000000002', t_id, pv_private, 'N02BE01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE,  'GENERIC',    'A', FALSE, NOW(), 'Paracetamol 500 mg tablet'),
      ('d6000000-0002-4000-8000-000000000003', t_id, pv_private, 'A10BA02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE,  'GENERIC',    'A', FALSE, NOW(), 'Metformin 500 mg tablet'),
      ('d6000000-0002-4000-8000-000000000004', t_id, pv_private, 'C08CA01', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE,  'GENERIC',    'A', FALSE, NOW(), 'Amlodipine 5 mg tablet'),
      ('d6000000-0002-4000-8000-000000000005', t_id, pv_private, 'C09AA02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE,  'GENERIC',    'A', FALSE, NOW(), 'Enalapril 5 mg tablet'),
      ('d6000000-0002-4000-8000-000000000006', t_id, pv_private, 'P01BF01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE,  'PREFERRED',  'B', FALSE, NOW(), 'Artemether + lumefantrine 20/120 tablet'),
      ('d6000000-0002-4000-8000-000000000007', t_id, pv_private, 'R03AC02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE,  'PREFERRED',  'B', FALSE, NOW(), 'Salbutamol 100 mcg/dose inhaler'),
      ('d6000000-0002-4000-8000-000000000008', t_id, pv_private, 'A10AB01', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE,  'PREFERRED',  'B', FALSE, NOW(), 'Insulin (human, soluble) 100 IU/mL'),
      ('d6000000-0002-4000-8000-000000000009', t_id, pv_private, 'J01DD04', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE,  'SPECIALTY',  'C', TRUE,  NOW(), 'Ceftriaxone 1 g injection — PA required'),
      ('d6000000-0002-4000-8000-000000000010', t_id, pv_private, 'N02AA01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE,  'CONTROLLED', 'C', TRUE,  NOW(), 'Morphine 10 mg/mL injection — controlled, PA required'),
      ('d6000000-0002-4000-8000-000000000011', t_id, pv_private, 'N05BA01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   FALSE, 'STANDARD',   NULL, FALSE, NOW(), 'Diazepam 5 mg tablet — excluded from cover')
    ON CONFLICT (plan_version_id, medication_code, coding_system, effective_from) DO NOTHING;

    -- MOHCC National Core: same 10 covered medicines, fully covered, no PA
    -- (public-sector posture); diazepam covered here (public supply chain).
    INSERT INTO cv_formulary (id, tenant_id, plan_version_id, medication_code, coding_system,
        benefit_code, covered, tier, copay_class, requires_authorisation, effective_from, notes)
    VALUES
      ('d6000000-0001-4000-8000-000000000001', t_id, pv_mohcc, 'J01CA04', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Amoxicillin 500 mg capsule'),
      ('d6000000-0001-4000-8000-000000000002', t_id, pv_mohcc, 'N02BE01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Paracetamol 500 mg tablet'),
      ('d6000000-0001-4000-8000-000000000003', t_id, pv_mohcc, 'A10BA02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Metformin 500 mg tablet'),
      ('d6000000-0001-4000-8000-000000000004', t_id, pv_mohcc, 'C08CA01', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Amlodipine 5 mg tablet'),
      ('d6000000-0001-4000-8000-000000000005', t_id, pv_mohcc, 'C09AA02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Enalapril 5 mg tablet'),
      ('d6000000-0001-4000-8000-000000000006', t_id, pv_mohcc, 'P01BF01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Artemether + lumefantrine 20/120 tablet'),
      ('d6000000-0001-4000-8000-000000000007', t_id, pv_mohcc, 'R03AC02', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Salbutamol 100 mcg/dose inhaler'),
      ('d6000000-0001-4000-8000-000000000008', t_id, pv_mohcc, 'A10AB01', 'http://www.whocc.no/atc',
       'CHRONIC_MEDS', TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Insulin (human, soluble) 100 IU/mL'),
      ('d6000000-0001-4000-8000-000000000009', t_id, pv_mohcc, 'J01DD04', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'GENERIC',    NULL, FALSE, NOW(), 'Ceftriaxone 1 g injection'),
      ('d6000000-0001-4000-8000-000000000010', t_id, pv_mohcc, 'N02AA01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'CONTROLLED', NULL, TRUE,  NOW(), 'Morphine 10 mg/mL injection — controlled register'),
      ('d6000000-0001-4000-8000-000000000011', t_id, pv_mohcc, 'N05BA01', 'http://www.whocc.no/atc',
       'ACUTE_MEDS',   TRUE, 'CONTROLLED', NULL, FALSE, NOW(), 'Diazepam 5 mg tablet')
    ON CONFLICT (plan_version_id, medication_code, coding_system, effective_from) DO NOTHING;
END $$;
