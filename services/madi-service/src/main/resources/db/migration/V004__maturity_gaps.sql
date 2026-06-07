-- MADI maturity gaps: NHUME handoff linkage + forecast epidemiology metadata
-- Service : madi-service
-- Flyway  : V004__maturity_gaps.sql

ALTER TABLE madi.blood_emergency_redistributions
    ADD COLUMN IF NOT EXISTS nhume_delivery_id UUID,
    ADD COLUMN IF NOT EXISTS handoff_status VARCHAR(32) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS handoff_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS handoff_error TEXT;

ALTER TABLE madi.blood_forecasts
    ADD COLUMN IF NOT EXISTS epidemiology_multiplier DECIMAL(6,3) DEFAULT 1.000,
    ADD COLUMN IF NOT EXISTS epidemiology_signals_json JSONB DEFAULT '[]';
