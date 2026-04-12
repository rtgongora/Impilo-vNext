-- CompanionOutboxPublisher: occurred_at + publish_error

ALTER TABLE camp.event_outbox ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMPTZ;
UPDATE camp.event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
ALTER TABLE camp.event_outbox ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE camp.event_outbox ALTER COLUMN occurred_at SET DEFAULT now();

ALTER TABLE camp.event_outbox ADD COLUMN IF NOT EXISTS publish_error TEXT;
