-- Wave 5.1 — patient-level duplicate order guard lookup index.
CREATE INDEX IF NOT EXISTS idx_oros_orders_patient_code_placed
    ON oros_orders (tenant_id, patient_cpid, zibo_order_code, placed_at)
    WHERE zibo_order_code IS NOT NULL;
