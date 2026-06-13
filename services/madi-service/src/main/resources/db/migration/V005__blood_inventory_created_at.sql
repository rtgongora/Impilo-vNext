-- Align blood_inventory_balances with Hibernate entity (created_at audit column).
ALTER TABLE madi.blood_inventory_balances
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
