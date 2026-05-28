-- CompanionOutboxPublisher poison-message handling (renumbered from duplicate V003).

ALTER TABLE surv.event_outbox
    ADD COLUMN IF NOT EXISTS publish_error TEXT;

ALTER TABLE surv.event_outbox
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
