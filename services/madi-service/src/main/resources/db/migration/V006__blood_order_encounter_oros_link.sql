-- =============================================
-- Service : madi-service
-- Flyway  : V006__blood_order_encounter_oros_link.sql
-- Purpose : Tie blood-bank orders to the clinical encounter that triggered them and track the
--           OROS integration state, so transfusion orders are traceable end to end and the
--           bidirectional OROS <-> MADI loop is auditable.
-- =============================================

ALTER TABLE blood_orders
    ADD COLUMN IF NOT EXISTS encounter_ref            VARCHAR(128),
    ADD COLUMN IF NOT EXISTS oros_integration_status  VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_blood_orders_encounter_ref
    ON blood_orders (encounter_ref) WHERE encounter_ref IS NOT NULL;
