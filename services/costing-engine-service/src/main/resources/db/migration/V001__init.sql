-- =============================================
-- Service : costa-costing-engine-service (COSTA)
-- Flyway  : V001__init.sql
-- Purpose : Full schema for the Impilo Costing Engine
-- =============================================

-- ----- Cost Method Configuration -----

CREATE TABLE costa_cost_methods (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    method_type     VARCHAR(20)     NOT NULL CHECK (method_type IN ('MICRO','ABC','TARIFF','STANDARD','STOCK_AVG')),
    config          JSONB           NOT NULL DEFAULT '{}',
    version         INT             NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name, version)
);

CREATE INDEX idx_costa_cost_methods_tenant ON costa_cost_methods (tenant_id, status);

-- ----- Unit Cost Sources -----

CREATE TABLE costa_unit_cost_sources (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    source_type     VARCHAR(20)     NOT NULL CHECK (source_type IN ('INVENTORY','TARIFF','MANUAL','ABC_POOL')),
    msika_code      VARCHAR(50),
    ref             JSONB           NOT NULL DEFAULT '{}',
    unit_cost       NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_ucs_tenant_code ON costa_unit_cost_sources (tenant_id, msika_code, effective_from DESC);

-- ----- ABC Cost Pools -----

CREATE TABLE costa_abc_cost_pools (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID,
    pool_name       VARCHAR(100)    NOT NULL,
    annual_cost     NUMERIC(14,2)   NOT NULL,
    driver_type     VARCHAR(50)     NOT NULL,
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_abc_pools_tenant ON costa_abc_cost_pools (tenant_id, facility_id);

-- ----- ABC Driver Rates -----

CREATE TABLE costa_abc_driver_rates (
    id              BIGSERIAL       PRIMARY KEY,
    pool_id         BIGINT          NOT NULL REFERENCES costa_abc_cost_pools(id),
    driver_unit     VARCHAR(50)     NOT NULL,
    annual_volume   NUMERIC(14,2)   NOT NULL,
    rate            NUMERIC(14,6)   NOT NULL,
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_abc_drivers_pool ON costa_abc_driver_rates (pool_id, effective_from DESC);

-- ----- Tariffs -----

CREATE TABLE costa_tariffs (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    tariff_code     VARCHAR(50)     NOT NULL,
    msika_code      VARCHAR(50),
    description     VARCHAR(500),
    price           NUMERIC(14,2)   NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    facility_category VARCHAR(50),
    patient_category  VARCHAR(50),
    rules           JSONB           NOT NULL DEFAULT '{}',
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    version         INT             NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_tariffs_lookup ON costa_tariffs (tenant_id, tariff_code, effective_from DESC);
CREATE INDEX idx_costa_tariffs_msika ON costa_tariffs (tenant_id, msika_code, effective_from DESC);

-- ----- Charging Rulesets -----

CREATE TABLE costa_charging_rulesets (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    version         INT             NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    rules           JSONB           NOT NULL DEFAULT '[]',
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_by      VARCHAR(100),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name, version)
);

CREATE INDEX idx_costa_rulesets_tenant ON costa_charging_rulesets (tenant_id, status, effective_from DESC);

-- ----- Exemption Rules -----

CREATE TABLE costa_exemption_rules (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    category        VARCHAR(50)     NOT NULL CHECK (category IN ('CHILD','ELDERLY','MATERNITY','INDIGENT','PROGRAM_FUNDED','STAFF','CHRONIC','VETERAN','DISABILITY','OTHER')),
    version         INT             NOT NULL DEFAULT 1,
    rules           JSONB           NOT NULL DEFAULT '{}',
    discount_pct    NUMERIC(5,2)    NOT NULL DEFAULT 100.00,
    max_amount      NUMERIC(14,2),
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_exemptions_tenant ON costa_exemption_rules (tenant_id, category, status);

-- ----- Insurance Plans -----

CREATE TABLE costa_insurance_plans (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    insurer_name    VARCHAR(200)    NOT NULL,
    plan_code       VARCHAR(50)     NOT NULL,
    plan_name       VARCHAR(200),
    coverage_pct    NUMERIC(5,2)    NOT NULL DEFAULT 80.00,
    annual_cap      NUMERIC(14,2),
    deductible      NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    copay_pct       NUMERIC(5,2)    NOT NULL DEFAULT 20.00,
    copay_fixed     NUMERIC(14,2),
    prior_auth_required BOOLEAN     NOT NULL DEFAULT false,
    covered_categories TEXT[]        NOT NULL DEFAULT '{}',
    excluded_codes  TEXT[]          NOT NULL DEFAULT '{}',
    rules           JSONB           NOT NULL DEFAULT '{}',
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_insurance_tenant ON costa_insurance_plans (tenant_id, plan_code, status);

-- ----- Encounters (local costing view) -----

CREATE TABLE costa_encounters (
    encounter_id    VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    patient_cpid    VARCHAR(50)     NOT NULL,
    encounter_type  VARCHAR(30)     NOT NULL DEFAULT 'OUTPATIENT' CHECK (encounter_type IN ('OUTPATIENT','INPATIENT','DAYCASE','OUTREACH','DELIVERY','EMERGENCY')),
    pct_journey_id  VARCHAR(50),
    pct_ref         JSONB           NOT NULL DEFAULT '{}',
    insurance_plan_id BIGINT        REFERENCES costa_insurance_plans(id),
    exemption_category VARCHAR(50),
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','FINALIZED')),
    opened_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_encounters_patient ON costa_encounters (patient_cpid, opened_at DESC);
CREATE INDEX idx_costa_encounters_tenant ON costa_encounters (tenant_id, facility_id, status, created_at DESC);

-- ----- Bill Headers -----

CREATE TABLE costa_bill_headers (
    bill_id         VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    encounter_id    VARCHAR(26)     REFERENCES costa_encounters(encounter_id),
    msika_order_id  VARCHAR(50),
    bill_type       VARCHAR(30)     NOT NULL DEFAULT 'ENCOUNTER' CHECK (bill_type IN ('ENCOUNTER','ESTIMATE','MARKETPLACE','OUTREACH')),
    status          VARCHAR(30)     NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACCUMULATING','APPROVAL_PENDING','APPROVED','FINAL','VOID','ADJUSTED')),
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    total_cost      NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    total_charge    NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    total_discount  NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    total_payable   NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    patient_payable NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    insurer_payable NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    subsidy_payable NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    write_off       NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    trace_summary   JSONB           NOT NULL DEFAULT '{}',
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    finalized_at    TIMESTAMPTZ,
    voided_at       TIMESTAMPTZ
);

CREATE INDEX idx_costa_bills_tenant ON costa_bill_headers (tenant_id, facility_id, status, created_at DESC);
CREATE INDEX idx_costa_bills_encounter ON costa_bill_headers (encounter_id);

-- ----- Bill Lines -----

CREATE TABLE costa_bill_lines (
    line_id         VARCHAR(26)     PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    msika_code      VARCHAR(50),
    description     VARCHAR(500)    NOT NULL,
    kind            VARCHAR(30)     NOT NULL CHECK (kind IN ('SERVICE','PRODUCT','FEE','BEDDAY','FOOD','LAUNDRY','OXYGEN','THEATRE','IMPLANT','TRANSPORT','PER_DIEM','COLD_CHAIN','NURSING','OVERHEAD','OTHER')),
    qty             NUMERIC(10,3)   NOT NULL DEFAULT 1,
    unit_price      NUMERIC(14,2)   NOT NULL,
    amount          NUMERIC(14,2)   NOT NULL,
    cost_amount     NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    cost_method_used VARCHAR(20),
    cost_trace      JSONB           NOT NULL DEFAULT '{}',
    charge_trace    JSONB           NOT NULL DEFAULT '{}',
    restriction_snapshot JSONB      NOT NULL DEFAULT '{}',
    source_event    VARCHAR(100),
    source_ref      VARCHAR(100),
    posted_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    voided          BOOLEAN         NOT NULL DEFAULT false
);

CREATE INDEX idx_costa_bill_lines_bill ON costa_bill_lines (bill_id);

-- ----- Bill Parties (split) -----

CREATE TABLE costa_bill_parties (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    party_type      VARCHAR(20)     NOT NULL CHECK (party_type IN ('PATIENT','INSURER','SUBSIDY','WRITE_OFF','PROGRAM','DONOR')),
    party_ref       VARCHAR(100),
    amount          NUMERIC(14,2)   NOT NULL,
    reason_codes    TEXT[]          NOT NULL DEFAULT '{}',
    trace           JSONB           NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_bill_parties_bill ON costa_bill_parties (bill_id);

-- ----- Approvals -----

CREATE TABLE costa_approvals (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    step            VARCHAR(30)     NOT NULL CHECK (step IN ('CLINICAL_REVIEW','FINANCE_REVIEW','MANAGEMENT_OVERRIDE','POST_CLOSE_ADJUSTMENT')),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    approver_actor_id VARCHAR(100),
    note            TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_costa_approvals_bill ON costa_approvals (bill_id, step);

-- ----- Invoices -----

CREATE TABLE costa_invoices (
    invoice_id      VARCHAR(26)     PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    invoice_number  VARCHAR(50)     NOT NULL,
    landela_doc_id  VARCHAR(100),
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_costa_invoices_bill ON costa_invoices (bill_id);
CREATE UNIQUE INDEX idx_costa_invoices_number ON costa_invoices (invoice_number);

-- ----- Payments -----

CREATE TABLE costa_payments (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    mushex_payment_intent_id VARCHAR(100),
    payment_type    VARCHAR(30)     NOT NULL DEFAULT 'FULL' CHECK (payment_type IN ('FULL','DEPOSIT','REMAINDER','THIRD_PARTY','REMITTANCE')),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','FAILED','CANCELLED')),
    amount          NUMERIC(14,2)   NOT NULL,
    paid_amount     NUMERIC(14,2)   NOT NULL DEFAULT 0.00,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_payments_bill ON costa_payments (bill_id);
CREATE UNIQUE INDEX idx_costa_payments_mushex ON costa_payments (mushex_payment_intent_id) WHERE mushex_payment_intent_id IS NOT NULL;

-- ----- Refunds -----

CREATE TABLE costa_refunds (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    refund_type     VARCHAR(20)     NOT NULL DEFAULT 'PARTIAL' CHECK (refund_type IN ('FULL','PARTIAL')),
    amount          NUMERIC(14,2)   NOT NULL,
    reason          TEXT            NOT NULL,
    reason_code     VARCHAR(50),
    mushex_refund_id VARCHAR(100),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSED','FAILED')),
    approver_actor_id VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_costa_refunds_bill ON costa_refunds (bill_id);

-- ----- Claim Packs -----

CREATE TABLE costa_claim_packs (
    id              BIGSERIAL       PRIMARY KEY,
    bill_id         VARCHAR(26)     NOT NULL REFERENCES costa_bill_headers(bill_id),
    insurer_ref     VARCHAR(100),
    claim_type      VARCHAR(30)     NOT NULL DEFAULT 'INITIAL' CHECK (claim_type IN ('INITIAL','SUPPLEMENTARY','REVERSAL')),
    payload         JSONB           NOT NULL DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','ACKNOWLEDGED','REJECTED','FAILED')),
    sent_at         TIMESTAMPTZ,
    response        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_claims_bill ON costa_claim_packs (bill_id);
CREATE INDEX idx_costa_claims_status ON costa_claim_packs (status, created_at);

-- ----- Idempotency -----

CREATE TABLE costa_idempotency (
    idempotency_key VARCHAR(200)    PRIMARY KEY,
    source_system   VARCHAR(50)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ----- Event Outbox -----

CREATE TABLE costa_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_costa_outbox_unpublished ON costa_event_outbox (created_at) WHERE published_at IS NULL;
