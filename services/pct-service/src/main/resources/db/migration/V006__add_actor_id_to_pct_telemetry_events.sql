ALTER TABLE pct_telemetry_events
    ADD COLUMN IF NOT EXISTS actor_id VARCHAR(255);
