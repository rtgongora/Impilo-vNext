-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V013__controlled_drug_register.sql
-- Prefix  : inv_
--
-- Dura Wave 3: controlled-drug witness register.
--
-- A running ledger of controlled-drug movements (issue / administer / waste /
-- discard) against a reference (typically a theatre case / episode). Every
-- administration, waste or discard requires a witnessing provider; the running
-- balance is carried forward per (tenant, item_code, ref_id).
-- =============================================

CREATE TABLE inv_controlled_drug_register (
    entry_id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    facility_id             UUID,
    store_id                UUID,
    item_code               VARCHAR(64)    NOT NULL,
    drug_name               VARCHAR(255),
    action                  VARCHAR(16)    NOT NULL,   -- ISSUE | ADMINISTER | WASTE | DISCARD
    quantity                NUMERIC(12,3)  NOT NULL,
    unit                    VARCHAR(24),
    running_balance         NUMERIC(12,3),
    administering_provider  VARCHAR(128),
    witness_provider        VARCHAR(128),
    ref_type                VARCHAR(40)    DEFAULT 'THEATRE_CASE',
    ref_id                  VARCHAR(128),
    chart_entry_ref         VARCHAR(128),
    reconciled              BOOLEAN        NOT NULL DEFAULT FALSE,
    notes                   TEXT,
    created_by              VARCHAR(128),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_cdr_ref  ON inv_controlled_drug_register(tenant_id, ref_type, ref_id);
CREATE INDEX idx_cdr_item ON inv_controlled_drug_register(tenant_id, item_code);
