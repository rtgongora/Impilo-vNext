-- Outbox poison-message handling + appeal appellant metadata
ALTER TABLE cv_event_outbox
    ADD COLUMN IF NOT EXISTS publish_error TEXT,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE cv_appeals
    ADD COLUMN IF NOT EXISTS appellant_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS appellant_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reviewer_id VARCHAR(255);
