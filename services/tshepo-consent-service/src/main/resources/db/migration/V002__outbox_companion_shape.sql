-- tshepo-consent-service V002 — event_outbox to the companion v1.1 shape.
--
-- The table had no tenant_id at all, so nothing it published could carry federation
-- context; the v1.1 envelope would have had to invent one. Both consent aggregates
-- already hold a tenant, so the backfill resolves rather than assumes.
--
-- pod_id defaults to 'national', NOT 'national-spine'. FederationAuthority
-- .NATIONAL_POD_ID is "national" and isNational() compares against it, so the literal
-- used across ~58 migration files in this estate is the one value it does not
-- recognise.

ALTER TABLE tshepo_consent.event_outbox RENAME COLUMN payload TO payload_json;

ALTER TABLE tshepo_consent.event_outbox
    ADD COLUMN IF NOT EXISTS event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64)     NOT NULL DEFAULT 'tshepo-consent-service',
    ADD COLUMN IF NOT EXISTS tenant_id       UUID,
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national',
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER         NOT NULL DEFAULT 0;

UPDATE tshepo_consent.event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
UPDATE tshepo_consent.event_outbox SET subject_id = aggregate_id WHERE subject_id IS NULL;
UPDATE tshepo_consent.event_outbox SET subject_type = aggregate_type WHERE subject_type IS NULL;
UPDATE tshepo_consent.event_outbox SET idempotency_key = event_id::text WHERE idempotency_key IS NULL;

-- Resolve the tenant from whichever aggregate the row belongs to.
UPDATE tshepo_consent.event_outbox o
   SET tenant_id = d.tenant_id
  FROM tshepo_consent.consent_directive d
 WHERE o.tenant_id IS NULL
   AND o.aggregate_type = 'ConsentDirective'
   AND o.aggregate_id = d.id::text;

UPDATE tshepo_consent.event_outbox o
   SET tenant_id = s.tenant_id
  FROM tshepo_consent.share_link s
 WHERE o.tenant_id IS NULL
   AND o.aggregate_type = 'ShareLink'
   AND o.aggregate_id = s.id::text;

-- Refuse rather than invent: a row whose tenant cannot be resolved from its own
-- aggregate has no truthful tenant_id, and a placeholder here would be published as
-- federation truth. Measured 2026-08-07: tshepo_consent holds 4 rows, all published.
DO $$
DECLARE
    orphaned BIGINT;
BEGIN
    SELECT count(*) INTO orphaned FROM tshepo_consent.event_outbox WHERE tenant_id IS NULL;
    IF orphaned > 0 THEN
        RAISE EXCEPTION
            'tshepo_consent.event_outbox has % row(s) with no resolvable tenant_id. '
            'Resolve or remove them before applying V002.', orphaned;
    END IF;
END $$;

ALTER TABLE tshepo_consent.event_outbox
    ALTER COLUMN tenant_id       SET NOT NULL,
    ALTER COLUMN subject_id      SET NOT NULL,
    ALTER COLUMN subject_type    SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN occurred_at     SET NOT NULL,
    ALTER COLUMN occurred_at     SET DEFAULT now();

ALTER TABLE tshepo_consent.event_outbox
    ADD CONSTRAINT uq_consent_outbox_idempotency UNIQUE (idempotency_key);
