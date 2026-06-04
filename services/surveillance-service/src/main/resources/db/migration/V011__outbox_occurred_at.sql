-- Align surv.event_outbox with EventOutboxEntity (CompanionOutboxPublisher timestamps).

ALTER TABLE surv.event_outbox
    ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;

UPDATE surv.event_outbox
SET occurred_at = created_at
WHERE occurred_at IS NULL;
