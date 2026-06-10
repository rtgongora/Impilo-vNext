-- Link campaigns to governed Impilo Live public broadcasts (doctrine §4).
ALTER TABLE camp.campaigns
    ADD COLUMN IF NOT EXISTS live_event_id UUID;

CREATE INDEX IF NOT EXISTS idx_campaigns_live_event
    ON camp.campaigns (live_event_id)
    WHERE live_event_id IS NOT NULL;
