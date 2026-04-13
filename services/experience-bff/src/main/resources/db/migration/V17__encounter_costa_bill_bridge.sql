-- V17: Add COSTA bill bridge column to encounters.
-- Stores the bill ID returned by the costing-engine service when a
-- bill draft is created on encounter close/discharge.

ALTER TABLE encounters ADD COLUMN costa_bill_id VARCHAR(26);

CREATE INDEX idx_encounters_costa_bill_id ON encounters (costa_bill_id)
    WHERE costa_bill_id IS NOT NULL;

COMMENT ON COLUMN encounters.costa_bill_id IS 'ULID-26 bill reference from COSTA costing-engine, set on encounter close/discharge';
