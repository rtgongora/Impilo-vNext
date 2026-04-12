-- CompanionOutboxPublisher poison-message handling
ALTER TABLE inv_event_outbox
    ADD COLUMN IF NOT EXISTS publish_error TEXT;

ALTER TABLE inv_event_outbox
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
