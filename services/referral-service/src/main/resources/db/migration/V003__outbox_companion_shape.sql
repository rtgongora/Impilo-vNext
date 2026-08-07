-- referral-service V003 — event_outbox to the companion v1.1 shape.
--
-- referral.event_outbox carried only aggregate/event/payload, so a v1.1 envelope
-- built from it had no federation context at all. These columns are what
-- CompanionOutboxPublisher reads to populate EventEnvelope.
--
-- pod_id defaults to 'national', NOT 'national-spine'. The literal matters:
-- FederationAuthority.NATIONAL_POD_ID is "national" and isNational() compares
-- against it, so a row defaulted to 'national-spine' is not recognised as
-- national by the very helper that gates national-authoritative operations.
-- ~58 migration files across the estate carry the wrong literal; this one does not.

ALTER TABLE referral.event_outbox RENAME COLUMN payload TO payload_json;

ALTER TABLE referral.event_outbox
    ADD COLUMN IF NOT EXISTS event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64)     NOT NULL DEFAULT 'referral-service',
    ADD COLUMN IF NOT EXISTS tenant_id       UUID,
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national',
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER         NOT NULL DEFAULT 0;

-- Backfill the columns that can be derived from the row itself.
UPDATE referral.event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
UPDATE referral.event_outbox SET subject_id = aggregate_id WHERE subject_id IS NULL;
UPDATE referral.event_outbox SET subject_type = aggregate_type WHERE subject_type IS NULL;
UPDATE referral.event_outbox SET idempotency_key = event_id::text WHERE idempotency_key IS NULL;

-- Backfill tenant from the owning aggregate. Both referral aggregates carry
-- tenant_id, so this is a resolved value rather than an assumed one.
UPDATE referral.event_outbox o
   SET tenant_id = r.tenant_id
  FROM referral.referrals r
 WHERE o.tenant_id IS NULL
   AND o.aggregate_type = 'Referral'
   AND o.aggregate_id = r.id::text;

UPDATE referral.event_outbox o
   SET tenant_id = s.tenant_id
  FROM referral.surgical_referral s
 WHERE o.tenant_id IS NULL
   AND o.aggregate_type = 'SurgicalReferral'
   AND o.aggregate_id = s.id::text;

-- Refuse rather than invent. A row whose tenant cannot be resolved from its own
-- aggregate has no truthful tenant_id, and writing a placeholder here is the
-- fabrication the envelope contract exists to prevent. Measured 2026-08-07:
-- every environment checked holds 0 unpublished rows, so this raises only if
-- an environment genuinely has orphaned outbox history to look at first.
DO $$
DECLARE
    orphaned BIGINT;
BEGIN
    SELECT count(*) INTO orphaned FROM referral.event_outbox WHERE tenant_id IS NULL;
    IF orphaned > 0 THEN
        RAISE EXCEPTION
            'referral.event_outbox has % row(s) with no resolvable tenant_id. '
            'Resolve or remove them before applying V003 — a placeholder tenant '
            'would be published as federation truth.', orphaned;
    END IF;
END $$;

ALTER TABLE referral.event_outbox
    ALTER COLUMN tenant_id       SET NOT NULL,
    ALTER COLUMN subject_id      SET NOT NULL,
    ALTER COLUMN subject_type    SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN occurred_at     SET NOT NULL,
    ALTER COLUMN occurred_at     SET DEFAULT now();

ALTER TABLE referral.event_outbox
    ADD CONSTRAINT uq_referral_outbox_idempotency UNIQUE (idempotency_key);
