-- V13: Enhance prescriptions with Lovable-aligned medication fields.
-- Adds route, instructions, indication, and generic name for richer prescribing.

ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS route VARCHAR(50);
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS instructions TEXT;
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS indication TEXT;
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS generic_name VARCHAR(500);
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS dispensed_by VARCHAR(255);
ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS dispensed_at TIMESTAMPTZ;
