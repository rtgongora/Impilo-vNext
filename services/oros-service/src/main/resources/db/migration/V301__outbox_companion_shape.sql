-- oros-service V301 — oros_event_outbox to the companion v1.1 shape.
--
-- oros already carried tenant_id, so unlike referral and consent this migration adds no
-- tenant: it adds the rest of the envelope context the table never had.
--
-- pod_id defaults to 'national', NOT 'national-spine'. FederationAuthority.NATIONAL_POD_ID
-- is "national" and isNational() compares against it, so the literal used across ~58
-- migration files in this estate is the one value it does not recognise.
--
-- subject_* is backfilled from the aggregate rather than resolved to a patient. An order's
-- subject genuinely is the order as far as this table knows; joining oros_orders to claim a
-- patient subject for 12,773 historical rows would assert a link the row never recorded.

ALTER TABLE oros_event_outbox RENAME COLUMN payload TO payload_json;

ALTER TABLE oros_event_outbox
    ADD COLUMN IF NOT EXISTS event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64)     NOT NULL DEFAULT 'oros-service',
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national',
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER         NOT NULL DEFAULT 0;

UPDATE oros_event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
UPDATE oros_event_outbox SET subject_id = aggregate_id WHERE subject_id IS NULL;
UPDATE oros_event_outbox SET subject_type = aggregate_type WHERE subject_type IS NULL;
UPDATE oros_event_outbox SET idempotency_key = event_id::text WHERE idempotency_key IS NULL;

-- tenant_id is already NOT NULL on this table, so there is no orphan case to refuse here.
ALTER TABLE oros_event_outbox
    ALTER COLUMN subject_id      SET NOT NULL,
    ALTER COLUMN subject_type    SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN occurred_at     SET NOT NULL,
    ALTER COLUMN occurred_at     SET DEFAULT now();

ALTER TABLE oros_event_outbox
    ADD CONSTRAINT uq_oros_outbox_idempotency UNIQUE (idempotency_key);
