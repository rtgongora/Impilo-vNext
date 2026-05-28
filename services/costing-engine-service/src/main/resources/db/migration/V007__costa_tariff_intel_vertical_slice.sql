-- COSTA vertical slice: tariff library / lists, charge sheets, cost estimate snapshots, payment handoffs.
-- Doctrine: costing vs billing separation is enforced in application logic; this schema stores library metadata,
-- tariff list items (placeholder prices), charge sheet capture, and settlement handoff audit rows.

CREATE TABLE costa_tariff_libraries (
    id              BIGSERIAL       PRIMARY KEY,
    code            VARCHAR(80)     NOT NULL UNIQUE,
    name            VARCHAR(300)    NOT NULL,
    description     TEXT,
    metadata        JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE costa_tariff_lists (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID,
    library_id          BIGINT          NOT NULL REFERENCES costa_tariff_libraries(id),
    external_code       VARCHAR(120)    NOT NULL,
    name                VARCHAR(400)    NOT NULL,
    description         TEXT,
    tariff_family       VARCHAR(80)     NOT NULL,
    tariff_type         VARCHAR(60)     NOT NULL,
    price_basis         VARCHAR(60)     NOT NULL,
    official_status     VARCHAR(60)     NOT NULL,
    validation_status   VARCHAR(60)     NOT NULL DEFAULT 'validated',
    reference_only      BOOLEAN         NOT NULL DEFAULT false,
    approved_for_billing BOOLEAN        NOT NULL DEFAULT false,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    effective_from      DATE            NOT NULL DEFAULT DATE '2020-01-01',
    effective_to        DATE,
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (library_id, external_code)
);

CREATE INDEX idx_costa_tariff_lists_tenant ON costa_tariff_lists (tenant_id);
CREATE INDEX idx_costa_tariff_lists_ref ON costa_tariff_lists (reference_only, approved_for_billing);

CREATE TABLE costa_tariff_list_items (
    id                  BIGSERIAL       PRIMARY KEY,
    tariff_list_id      BIGINT          NOT NULL REFERENCES costa_tariff_lists(id) ON DELETE CASCADE,
    item_code           VARCHAR(80)     NOT NULL,
    item_name           VARCHAR(400)    NOT NULL,
    service_domain      VARCHAR(120),
    service_category    VARCHAR(120),
    base_price          NUMERIC(14,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tariff_list_id, item_code)
);

CREATE TABLE costa_charge_sheets (
    charge_sheet_id     VARCHAR(26)     PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    patient_cpid        VARCHAR(80)     NOT NULL,
    encounter_id        VARCHAR(80),
    facility_id         UUID,
    workspace_id        UUID,
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    notes               TEXT,
    audit_metadata      JSONB           NOT NULL DEFAULT '{}',
    created_by          VARCHAR(120),
    submitted_by        VARCHAR(120),
    verified_by         VARCHAR(120),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    submitted_at        TIMESTAMPTZ,
    verified_at         TIMESTAMPTZ
);

CREATE TABLE costa_charge_sheet_items (
    charge_sheet_item_id VARCHAR(26)    PRIMARY KEY,
    charge_sheet_id      VARCHAR(26)   NOT NULL REFERENCES costa_charge_sheets(charge_sheet_id) ON DELETE CASCADE,
    item_category        VARCHAR(120)   NOT NULL,
    item_name            VARCHAR(400)   NOT NULL,
    item_code            VARCHAR(80),
    quantity             NUMERIC(14,4)  NOT NULL DEFAULT 1,
    unit_of_measure      VARCHAR(40),
    chargeable           BOOLEAN        NOT NULL DEFAULT true,
    duplicate_resolution VARCHAR(40),
    clinical_note        TEXT,
    metadata             JSONB          NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE costa_cost_estimates (
    cost_estimate_id    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    tariff_list_id      BIGINT          NOT NULL REFERENCES costa_tariff_lists(id),
    request_payload     JSONB           NOT NULL,
    response_payload    JSONB           NOT NULL,
    audit_reference       VARCHAR(120),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE costa_payment_handoffs (
    payment_handoff_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    invoice_id          VARCHAR(80),
    claim_id            VARCHAR(80),
    mushex_intent_id    VARCHAR(80),
    amount_requested    NUMERIC(14,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    status              VARCHAR(40)     NOT NULL DEFAULT 'CREATED',
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Seed: global tariff library + reference / demo lists (tenant_id NULL = platform-provided)
-- ---------------------------------------------------------------------------

INSERT INTO costa_tariff_libraries (code, name, description, metadata)
VALUES (
    'IMPILO_GLOBAL',
    'Impilo global tariff reference library',
    'Platform-level tariff libraries. Operational billing must use approved operational lists for the tenant context.',
    jsonb_build_object('doctrine', 'tariff_library_driven', 'warning', 'Reference libraries are not official billing tariffs unless imported and approved.')
);

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'ZW-PLACEHOLDER-POC-V0.1',
    'Zimbabwe Placeholder Health Services Tariff — Proof of Concept v0.1',
    'Placeholder proof-of-concept tariff for demos and automated tests. NOT an official national tariff. Prices are illustrative only.',
    'zimbabwe_placeholder', 'operational', 'placeholder_rate', 'placeholder', 'validated',
    false, true, 'USD', DATE '2020-01-01',
    jsonb_build_object(
        'provenance', 'Impilo synthetic seed',
        'disclaimer', 'Not an official Zimbabwe tariff.',
        'default_for_demo', true
    )
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'WHO-CHOICE-REF', 'WHO-CHOICE Unit Cost Reference', 'Economic costing reference — not an official patient billing tariff.', 'who_reference', 'economic_cost_reference', 'reference_rate', 'reference_only', 'reference_only', true, false, 'USD', DATE '2020-01-01', jsonb_build_object('url', 'https://www.who.int/activities/choosing-interventions-that-are-cost-effective')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'WHO-ICHI-REF', 'WHO ICHI Intervention Reference', 'Intervention classification reference — not a price list.', 'who_reference', 'classification_reference', 'reference_rate', 'reference_only', 'reference_only', true, false, 'USD', DATE '2020-01-01', jsonb_build_object('note', 'ICHI is a classification system, not a billing schedule.')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'NHS-ENGLAND-REF', 'NHS England Payment Scheme Reference', 'International reference example — reference-only unless imported and approved.', 'international_reference', 'reference', 'reference_rate', 'reference_only', 'reference_only', true, false, 'USD', DATE '2020-01-01', jsonb_build_object('region', 'England')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'AU-MBS-REF', 'Australia Medicare Benefits Schedule Reference', 'International reference example — reference-only unless imported and approved.', 'international_reference', 'reference', 'reference_rate', 'reference_only', 'reference_only', true, false, 'USD', DATE '2020-01-01', jsonb_build_object('region', 'Australia')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

INSERT INTO costa_tariff_lists (tenant_id, library_id, external_code, name, description, tariff_family, tariff_type, price_basis, official_status, validation_status, reference_only, approved_for_billing, currency, effective_from, metadata)
SELECT NULL, id, 'US-CMS-PHYSICIAN-REF', 'US CMS Medicare Physician Fee Schedule Reference', 'International reference example — reference-only unless imported and approved. Avoid embedding proprietary CPT descriptions.', 'international_reference', 'reference', 'reference_rate', 'reference_only', 'reference_only', true, false, 'USD', DATE '2020-01-01', jsonb_build_object('region', 'United States', 'licensing_note', 'Use official CMS licensing for operational imports.')
FROM costa_tariff_libraries WHERE code = 'IMPILO_GLOBAL';

-- Zimbabwe placeholder items (>=80). Prices are synthetic USD placeholders.
INSERT INTO costa_tariff_list_items (tariff_list_id, item_code, item_name, service_domain, service_category, base_price, currency, metadata)
SELECT tl.id, v.code, v.nm, v.dom, v.cat, v.pr, 'USD',
       jsonb_build_object(
           'reference_code_system', 'LOCAL',
           'source_inspiration', 'LOCAL_PLACEHOLDER',
           'official_status', 'placeholder',
           'validation_status', 'unvalidated',
           'disclaimer', 'Proof-of-concept placeholder only.'
       )
FROM costa_tariff_lists tl
CROSS JOIN (VALUES
 ('ZW-ADM-001','Patient registration','Registration and administration','registration',2.50),
 ('ZW-ADM-002','File retrieval / record administration','Registration and administration','administration',3.00),
 ('ZW-ADM-003','Health card replacement','Registration and administration','administration',4.00),
 ('ZW-ADM-004','Claim administration','Claims and payment administration','administration',6.00),
 ('ZW-ADM-005','Discharge administration','Registration and administration','administration',5.00),
 ('ZW-OPD-001','General outpatient consultation','OPD / primary care','consultation',8.00),
 ('ZW-OPD-002','Nurse-led consultation','OPD / primary care','consultation',6.00),
 ('ZW-OPD-003','Medical officer consultation','OPD / primary care','consultation',12.00),
 ('ZW-OPD-004','Review consultation','OPD / primary care','consultation',9.00),
 ('ZW-OPD-005','Vital signs assessment','OPD / primary care','nursing',3.50),
 ('ZW-OPD-006','Triage assessment','OPD / primary care','triage',4.00),
 ('ZW-EMR-001','Casualty consultation','Emergency care','consultation',15.00),
 ('ZW-EMR-002','Emergency observation','Emergency care','observation',20.00),
 ('ZW-EMR-003','Resuscitation bay use','Emergency care','resuscitation',35.00),
 ('ZW-EMR-004','Minor emergency procedure','Emergency care','procedure',25.00),
 ('ZW-EMR-005','Emergency referral coordination','Emergency care','coordination',7.00),
 ('ZW-MNH-001','Antenatal care visit','Maternal and newborn health','anc',10.00),
 ('ZW-MNH-002','Delivery admission','Maternal and newborn health','delivery',40.00),
 ('ZW-MNH-003','Normal delivery package','Maternal and newborn health','delivery',120.00),
 ('ZW-MNH-004','Assisted delivery placeholder','Maternal and newborn health','delivery',150.00),
 ('ZW-MNH-005','Caesarean section package placeholder','Maternal and newborn health','theatre',280.00),
 ('ZW-MNH-006','Postnatal care visit','Maternal and newborn health','pnc',12.00),
 ('ZW-MNH-007','Newborn check','Maternal and newborn health','newborn',8.00),
 ('ZW-MNH-008','Electronic partogram monitoring event','Maternal and newborn health','monitoring',18.00),
 ('ZW-CHD-001','Growth monitoring','Child health','growth',4.00),
 ('ZW-CHD-002','IMCI consultation','Child health','consultation',7.00),
 ('ZW-IMM-001','Vaccination administration','Child health / immunisation','immunisation',5.00),
 ('ZW-IMM-002','Cold chain handling placeholder','Child health / immunisation','logistics',3.00),
 ('ZW-CHD-003','Nutrition assessment','Child health','nutrition',5.50),
 ('ZW-HIV-001','HIV testing service','HIV services','testing',6.00),
 ('ZW-HIV-002','ART initiation visit','HIV services','art',12.00),
 ('ZW-HIV-003','ART review visit','HIV services','art',9.00),
 ('ZW-HIV-004','Viral load sample handling','HIV services','laboratory',11.00),
 ('ZW-HIV-005','Enhanced adherence counselling','HIV services','counselling',7.50),
 ('ZW-HIV-006','PrEP consultation','HIV services','prep',10.00),
 ('ZW-TB-001','TB screening','TB services','screening',4.00),
 ('ZW-TB-002','Sputum collection','TB services','sample',3.50),
 ('ZW-TB-003','TB follow-up visit','TB services','followup',6.00),
 ('ZW-TB-004','DOT support visit','TB services','adherence',5.00),
 ('ZW-TB-005','Contact tracing administration','TB services','public_health',6.50),
 ('ZW-NCD-001','Hypertension review','NCD services','review',8.00),
 ('ZW-NCD-002','Diabetes review','NCD services','review',9.00),
 ('ZW-NCD-003','Asthma/COPD review','NCD services','review',8.50),
 ('ZW-NCD-004','Mental health review','NCD services','review',12.00),
 ('ZW-NCD-005','NCD risk screening','NCD services','screening',5.00),
 ('ZW-LAB-001','Full blood count','Laboratory','haematology',9.00),
 ('ZW-LAB-002','Malaria rapid diagnostic test','Laboratory','rdt',4.50),
 ('ZW-LAB-003','HIV rapid test','Laboratory','rdt',4.50),
 ('ZW-LAB-004','Blood glucose test','Laboratory','chemistry',3.50),
 ('ZW-LAB-005','Urinalysis','Laboratory','urinalysis',4.00),
 ('ZW-LAB-006','Pregnancy test','Laboratory','immuno',3.00),
 ('ZW-LAB-007','Viral load processing placeholder','Laboratory','virology',22.00),
 ('ZW-LAB-008','Sample collection consumables','Laboratory','consumables',2.50),
 ('ZW-RAD-001','Plain X-ray','Imaging','radiology',18.00),
 ('ZW-RAD-002','Obstetric ultrasound','Imaging','ultrasound',25.00),
 ('ZW-RAD-003','General ultrasound','Imaging','ultrasound',22.00),
 ('ZW-RAD-004','CT scan placeholder','Imaging','ct',180.00),
 ('ZW-RAD-005','DICOM study storage/viewing event','Imaging','pacs',6.00),
 ('ZW-RAD-006','Contrast media placeholder','Imaging','contrast',30.00),
 ('ZW-PHM-001','Dispensing fee','Pharmacy','dispensing',2.00),
 ('ZW-PHM-002','Medicine item placeholder','Pharmacy','medicine',5.00),
 ('ZW-PHM-003','Chronic medicine refill handling','Pharmacy','refill',3.00),
 ('ZW-PHM-004','ART refill handling','Pharmacy','art',3.50),
 ('ZW-PHM-005','Controlled stock handling placeholder','Pharmacy','controlled',4.00),
 ('ZW-IPD-001','Primary hospital bed-day','Inpatient care','bed_day',35.00),
 ('ZW-IPD-002','District hospital bed-day','Inpatient care','bed_day',45.00),
 ('ZW-IPD-003','Provincial hospital bed-day','Inpatient care','bed_day',60.00),
 ('ZW-IPD-004','Central hospital bed-day','Inpatient care','bed_day',85.00),
 ('ZW-IPD-005','Nursing ward round','Inpatient care','nursing',10.00),
 ('ZW-IPD-006','Inpatient observation','Inpatient care','observation',15.00),
 ('ZW-PRC-001','Minor procedure','Theatre and procedures','procedure',40.00),
 ('ZW-PRC-002','Major procedure placeholder','Theatre and procedures','procedure',220.00),
 ('ZW-PRC-003','Theatre time unit','Theatre and procedures','theatre',90.00),
 ('ZW-PRC-004','Suturing','Theatre and procedures','wound',12.00),
 ('ZW-PRC-005','Wound dressing','Theatre and procedures','wound',8.00),
 ('ZW-PRC-006','Catheterisation','Theatre and procedures','procedure',15.00),
 ('ZW-PRC-007','Cannulation','Theatre and procedures','procedure',6.00),
 ('ZW-PRC-008','Oxygen administration','Theatre and procedures','oxygen',10.00),
 ('ZW-REH-001','Physiotherapy session','Rehabilitation','physio',14.00),
 ('ZW-REH-002','Occupational therapy session','Rehabilitation','ot',14.00),
 ('ZW-REH-003','Rehabilitation assessment','Rehabilitation','assessment',18.00),
 ('ZW-REH-004','Assistive device fitting placeholder','Rehabilitation','devices',25.00),
 ('ZW-TEL-001','Virtual consultation','Telemedicine','telehealth',11.00),
 ('ZW-TEL-002','Remote specialist review','Telemedicine','telehealth',20.00),
 ('ZW-TEL-003','Telemedicine platform session','Telemedicine','platform',4.00),
 ('ZW-TEL-004','Remote follow-up','Telemedicine','telehealth',9.00),
 ('ZW-PBH-001','Community screening','Public health and outreach','screening',5.00),
 ('ZW-PBH-002','Outreach visit','Public health and outreach','outreach',12.00),
 ('ZW-PBH-003','Mobile clinic session','Public health and outreach','mobile',30.00),
 ('ZW-PBH-004','Contact tracing visit','Public health and outreach','tracing',8.00),
 ('ZW-PBH-005','Health education session','Public health and outreach','education',4.00),
 ('ZW-PAY-001','Invoice generation','Claims and payment administration','billing',2.00),
 ('ZW-PAY-002','Claim submission','Claims and payment administration','claims',3.00),
 ('ZW-PAY-003','Payment reversal administration','Claims and payment administration','admin',4.00),
 ('ZW-PAY-004','Wallet settlement administration','Claims and payment administration','wallet',3.50),
 ('ZW-PAY-005','Remittance processing administration','Claims and payment administration','remittance',4.50),
 ('ZW-CUS-001','Additional ward consumables sundry','Inpatient care','consumables',2.00),
 ('ZW-CUS-002','Oxygen delivery consumables','Inpatient care','oxygen',6.00),
 ('ZW-CUS-003','Sterile dressing pack','OPD / primary care','consumables',3.00)
) AS v(code, nm, dom, cat, pr)
WHERE tl.external_code = 'ZW-PLACEHOLDER-POC-V0.1';
