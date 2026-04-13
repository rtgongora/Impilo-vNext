-- V12: Add result data to lab orders for real result entry/display.
-- Stores structured result values (test name, value, unit, reference range,
-- interpretation) as JSONB, plus result metadata.

ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS result_data JSONB;
ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS result_notes TEXT;
ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS resulted_by VARCHAR(255);
ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS resulted_by_name VARCHAR(500);
ALTER TABLE lab_orders ADD COLUMN IF NOT EXISTS collected_by VARCHAR(255);
