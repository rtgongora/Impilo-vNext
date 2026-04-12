-- CompanionOutboxPublisher: occurred_at for envelope construction, publish_error for poison rows.

ALTER TABLE surv.event_outbox ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;
UPDATE surv.event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
ALTER TABLE surv.event_outbox ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE surv.event_outbox ALTER COLUMN occurred_at SET DEFAULT now();

ALTER TABLE surv.event_outbox ADD COLUMN IF NOT EXISTS publish_error TEXT;
