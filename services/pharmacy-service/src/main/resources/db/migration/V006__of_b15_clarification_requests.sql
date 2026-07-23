-- =============================================
-- Service : pharmacy-service
-- Database: pharmacy
-- Flyway  : V006__of_b15_clarification_requests.sql
-- OF-B15  : substitution → prescriber clarification loop (§8.10.1/§8.10.2)
-- =============================================

-- A substitution that requires prescriber approval (rule requires_step_up)
-- creates a clarification request; the episode holds while any request is
-- PENDING; decline is fail-closed (substitution never applied).
CREATE TABLE rx_clarification_requests (
    clarification_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         UUID,
    dispense_order_id   UUID NOT NULL REFERENCES rx_dispense_orders(order_id),
    dispense_item_id    UUID NOT NULL REFERENCES rx_dispense_items(item_id),
    source_drug_code    VARCHAR(50) NOT NULL,
    target_drug_code    VARCHAR(50) NOT NULL,
    rule_type           VARCHAR(30),
    reason              TEXT NOT NULL,
    -- §8.10 patient-consent flag: the patient was informed of and consented to
    -- the proposed substitution in plain language.
    patient_consent     BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by        VARCHAR(128) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    prescriber_ref      VARCHAR(128),
    resolved_by         VARCHAR(128),
    resolved_at         TIMESTAMPTZ,
    resolution_note     TEXT,
    -- set when an APPROVED clarification has been consumed by the substitution
    applied_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rx_clarifications_order_status
    ON rx_clarification_requests(dispense_order_id, status);

CREATE INDEX idx_rx_clarifications_item
    ON rx_clarification_requests(dispense_item_id);
