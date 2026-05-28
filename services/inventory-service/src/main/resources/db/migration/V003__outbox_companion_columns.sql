-- CompanionOutboxPublisher poison-message handling (renumbered from duplicate V003).

ALTER TABLE inv_event_outbox
    ADD COLUMN IF NOT EXISTS publish_error TEXT;

ALTER TABLE inv_event_outbox
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
