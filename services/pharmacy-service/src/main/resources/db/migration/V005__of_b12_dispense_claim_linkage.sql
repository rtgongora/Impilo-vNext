-- =============================================
-- Service : pharmacy-service
-- Database: pharmacy
-- Flyway  : V005__of_b12_dispense_claim_linkage.sql
-- OF-B12  : prescription-claim ↔ dispense-episode linkage (§13.3.1)
-- =============================================

-- A dispense episode created from an OROS PHARMACY order that carried a
-- prescription claim binds that claim; on completion the episode reference is
-- written back onto the OROS claim (claim_bound_at records the confirmed bind).
ALTER TABLE rx_dispense_orders ADD COLUMN prescription_claim_id UUID;
ALTER TABLE rx_dispense_orders ADD COLUMN claim_bound_at TIMESTAMPTZ;
ALTER TABLE rx_dispense_orders ADD COLUMN claim_released_at TIMESTAMPTZ;

-- §13.3.1 anomaly sweep marker: set once when a coded anomaly event
-- (UNBOUND_DISPENSE / CLAIM_EPISODE_TTL_EXPIRED) has been emitted for the row,
-- so anomalies are emitted exactly once.
ALTER TABLE rx_dispense_orders ADD COLUMN claim_anomaly_flagged_at TIMESTAMPTZ;

CREATE INDEX idx_rx_dispense_orders_claim
    ON rx_dispense_orders(prescription_claim_id)
    WHERE prescription_claim_id IS NOT NULL;
