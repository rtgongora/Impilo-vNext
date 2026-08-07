-- pharmacy-service V009 — rx_event_outbox to the companion v1.1 shape.
--
-- pharmacy already carried tenant_id, so this adds the rest of the envelope context the
-- table never had rather than a tenant.
--
-- pod_id defaults to 'national', NOT 'national-spine'. FederationAuthority.NATIONAL_POD_ID
-- is "national" and isNational() compares against it, so the literal used across ~58
-- migration files in this estate is the one value it does not recognise.
--
-- Nothing here changes what reaches pharmacy.dispense.complete. That topic feeds
-- InventoryEventConsumer.handleDispenseComplete, which reads tenantId/facilityId/storeId at
-- the JSON top level and, given an envelope, logs "missing tenant/facility/store/item,
-- skipping" instead of throwing — stock would silently stop being deducted. The legacy
-- topic keeps carrying payload_json verbatim; only the new impilo.pharmacy.* topic is
-- enveloped.

ALTER TABLE rx_event_outbox RENAME COLUMN payload TO payload_json;

ALTER TABLE rx_event_outbox
    ADD COLUMN IF NOT EXISTS event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64)     NOT NULL DEFAULT 'pharmacy-service',
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national',
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER         NOT NULL DEFAULT 0;

UPDATE rx_event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
UPDATE rx_event_outbox SET subject_id = aggregate_id WHERE subject_id IS NULL;
UPDATE rx_event_outbox SET subject_type = aggregate_type WHERE subject_type IS NULL;
UPDATE rx_event_outbox SET idempotency_key = event_id::text WHERE idempotency_key IS NULL;

-- tenant_id is already NOT NULL on this table, so there is no orphan case to refuse here.
ALTER TABLE rx_event_outbox
    ALTER COLUMN subject_id      SET NOT NULL,
    ALTER COLUMN subject_type    SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN occurred_at     SET NOT NULL,
    ALTER COLUMN occurred_at     SET DEFAULT now();

ALTER TABLE rx_event_outbox
    ADD CONSTRAINT uq_rx_outbox_idempotency UNIQUE (idempotency_key);
