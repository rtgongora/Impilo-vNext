-- tshepo-identity-service V007 — event_outbox to the companion v1.1 shape.
--
-- The table had no tenant_id column, so nothing it published could carry federation
-- context. Every identity event does, however, already record the tenant inside its own
-- payload, so the backfill reads it from there rather than assuming one: measured
-- 2026-08-07, all 15 rows in the preview database (6 ProvisionalCpid, 9 ScopedToken)
-- carry a tenantId key.
--
-- pod_id defaults to 'national', NOT 'national-spine'. FederationAuthority.NATIONAL_POD_ID
-- is "national" and isNational() compares against it, so the literal used across ~58
-- migration files in this estate is the one value it does not recognise.

ALTER TABLE tshepo_identity.event_outbox RENAME COLUMN payload TO payload_json;

ALTER TABLE tshepo_identity.event_outbox
    ADD COLUMN IF NOT EXISTS event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS schema_version  INTEGER         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS causation_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS producer        VARCHAR(64)     NOT NULL DEFAULT 'tshepo-identity-service',
    ADD COLUMN IF NOT EXISTS tenant_id       UUID,
    ADD COLUMN IF NOT EXISTS pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national',
    ADD COLUMN IF NOT EXISTS subject_id      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_type    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS occurred_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS publish_error   TEXT,
    ADD COLUMN IF NOT EXISTS retry_count     INTEGER         NOT NULL DEFAULT 0;

UPDATE tshepo_identity.event_outbox SET occurred_at = created_at WHERE occurred_at IS NULL;
UPDATE tshepo_identity.event_outbox SET subject_id = aggregate_id WHERE subject_id IS NULL;
UPDATE tshepo_identity.event_outbox SET subject_type = aggregate_type WHERE subject_type IS NULL;
UPDATE tshepo_identity.event_outbox SET idempotency_key = event_id::text WHERE idempotency_key IS NULL;

-- The event's own payload is the record of which tenant it happened under. Reading it back
-- is recovery of a stated fact, not an assumption about an unstated one.
UPDATE tshepo_identity.event_outbox
   SET tenant_id = (payload_json ->> 'tenantId')::uuid
 WHERE tenant_id IS NULL
   AND payload_json ->> 'tenantId' IS NOT NULL
   AND payload_json ->> 'tenantId' ~ '^[0-9a-fA-F-]{36}$';

-- Refuse rather than invent. A row whose payload never recorded a tenant has no truthful
-- tenant_id, and a placeholder would be published as federation truth.
DO $$
DECLARE
    orphaned BIGINT;
BEGIN
    SELECT count(*) INTO orphaned FROM tshepo_identity.event_outbox WHERE tenant_id IS NULL;
    IF orphaned > 0 THEN
        RAISE EXCEPTION
            'tshepo_identity.event_outbox has % row(s) whose payload records no tenantId. '
            'Resolve or remove them before applying V007.', orphaned;
    END IF;
END $$;

ALTER TABLE tshepo_identity.event_outbox
    ALTER COLUMN tenant_id       SET NOT NULL,
    ALTER COLUMN subject_id      SET NOT NULL,
    ALTER COLUMN subject_type    SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL,
    ALTER COLUMN occurred_at     SET NOT NULL,
    ALTER COLUMN occurred_at     SET DEFAULT now();

ALTER TABLE tshepo_identity.event_outbox
    ADD CONSTRAINT uq_identity_outbox_idempotency UNIQUE (idempotency_key);
