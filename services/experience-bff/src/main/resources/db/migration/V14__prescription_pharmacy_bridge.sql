-- V14: Add pharmacy dispense order bridge column and enrich prescription tracking.
-- Stores the pharmacy-service dispense order ID for bidirectional tracking.

ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS pharmacy_dispense_order_id UUID;
CREATE INDEX IF NOT EXISTS idx_prescriptions_pharmacy_dispense ON prescriptions(pharmacy_dispense_order_id);
