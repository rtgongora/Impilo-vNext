-- Billing timing modes, invoice typing, and service access gate (COSTA).
-- Doctrine: costing is continuous; billing and settlement may occur before, during, or after care.

ALTER TABLE costa_invoices
    ADD COLUMN billing_timing_mode VARCHAR(40),
    ADD COLUMN invoice_type VARCHAR(50);

ALTER TABLE costa_billing_decisions
    ADD COLUMN billing_timing_mode VARCHAR(40);

ALTER TABLE costa_claim_packs
    ADD COLUMN billing_timing_mode VARCHAR(40);

ALTER TABLE costa_payment_handoffs
    ADD COLUMN billing_timing_mode VARCHAR(40);

ALTER TABLE costa_cost_estimates
    ADD COLUMN billing_timing_mode VARCHAR(40);

ALTER TABLE costa_charging_rulesets
    ADD COLUMN default_billing_timing_mode VARCHAR(40);

COMMENT ON COLUMN costa_charging_rulesets.default_billing_timing_mode IS
    'Default timing for ruleset; individual JSON rules may still override with billing_timing_mode per rule.';

CREATE TABLE costa_service_access_decisions (
    service_access_decision_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    patient_cpid VARCHAR(80),
    encounter_id VARCHAR(80),
    facility_id UUID,
    workspace_id UUID,
    requested_service_id VARCHAR(120),
    requested_service_name VARCHAR(400),
    requested_service_category VARCHAR(120),
    estimated_cost NUMERIC(14, 2),
    selected_tariff_list_id BIGINT REFERENCES costa_tariff_lists (id),
    tariffed_price NUMERIC(14, 2),
    billing_timing_mode VARCHAR(40),
    access_status VARCHAR(50) NOT NULL,
    required_deposit_amount NUMERIC(14, 2),
    required_payment_amount NUMERIC(14, 2),
    payer_id VARCHAR(120),
    coverage_reference VARCHAR(200),
    exemption_reference VARCHAR(200),
    waiver_reference VARCHAR(200),
    authorisation_reference VARCHAR(200),
    promise_to_pay_reference VARCHAR(200),
    decision_reason TEXT,
    decided_by VARCHAR(120),
    decision_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    audit_reference VARCHAR(120)
);

CREATE INDEX idx_service_access_tenant_encounter
    ON costa_service_access_decisions (tenant_id, encounter_id);
CREATE INDEX idx_service_access_tenant_patient
    ON costa_service_access_decisions (tenant_id, patient_cpid);
CREATE INDEX idx_service_access_decided_at
    ON costa_service_access_decisions (tenant_id, decision_timestamp DESC);
