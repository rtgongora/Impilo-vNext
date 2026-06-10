-- Link telehealth sessions to governed Impilo Live clinical events (doctrine §2).
ALTER TABLE pct.pct_telehealth_sessions
    ADD COLUMN IF NOT EXISTS live_event_id UUID;

CREATE INDEX IF NOT EXISTS idx_pct_telehealth_live_event
    ON pct.pct_telehealth_sessions (live_event_id)
    WHERE live_event_id IS NOT NULL;
