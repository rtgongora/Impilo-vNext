-- TSHEPO federation pod registration and audit-chain integrity upgrade.
--
-- V002 was deployed before the audit-chain race mitigations existed. Keep that
-- migration immutable for deployed databases and apply the upgrade here.

ALTER TABLE tshepo.audit_event
    ADD COLUMN IF NOT EXISTS sequence_num BIGINT;

WITH numbered AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id
               ORDER BY occurred_at ASC, id ASC
           ) AS seq
    FROM tshepo.audit_event
)
UPDATE tshepo.audit_event audit_event
SET sequence_num = numbered.seq
FROM numbered
WHERE audit_event.id = numbered.id
  AND audit_event.sequence_num IS NULL;

ALTER TABLE tshepo.audit_event
    ALTER COLUMN sequence_num SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_audit_chain_link'
          AND conrelid = 'tshepo.audit_event'::regclass
    ) THEN
        ALTER TABLE tshepo.audit_event
            ADD CONSTRAINT uq_audit_chain_link UNIQUE (tenant_id, prev_hash);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_audit_sequence'
          AND conrelid = 'tshepo.audit_event'::regclass
    ) THEN
        ALTER TABLE tshepo.audit_event
            ADD CONSTRAINT uq_audit_sequence UNIQUE (tenant_id, sequence_num);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS tshepo.audit_chain_head (
    tenant_id       UUID PRIMARY KEY,
    current_hash    VARCHAR(64)  NOT NULL,
    current_seq     BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO tshepo.audit_chain_head (tenant_id, current_hash, current_seq, updated_at)
SELECT DISTINCT ON (tenant_id)
       tenant_id,
       entry_hash,
       sequence_num,
       now()
FROM tshepo.audit_event
ORDER BY tenant_id, sequence_num DESC
ON CONFLICT (tenant_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS tshepo.pod_registration (
    id                          BIGSERIAL PRIMARY KEY,
    registration_id             UUID         NOT NULL UNIQUE,
    pod_id                      UUID         NOT NULL UNIQUE,
    pod_name                    VARCHAR(255) NOT NULL,
    pod_certificate             TEXT         NOT NULL,
    capabilities                JSONB        NOT NULL,
    region                      VARCHAR(255),
    facility_ids                JSONB,
    granted_data_classes        JSONB        NOT NULL,
    max_offline_hours           INTEGER      NOT NULL,
    merge_authority             BOOLEAN      NOT NULL,
    revocation_priority         VARCHAR(255) NOT NULL,
    heartbeat_interval_seconds  INTEGER      NOT NULL,
    status                      VARCHAR(50)  NOT NULL,
    last_heartbeat_at           TIMESTAMPTZ,
    issued_at                   TIMESTAMPTZ  NOT NULL,
    expires_at                  TIMESTAMPTZ  NOT NULL,
    revoked_at                  TIMESTAMPTZ,
    revoked_by                  VARCHAR(255),
    revocation_reason           VARCHAR(255),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pod_registration_status
    ON tshepo.pod_registration (status);

CREATE INDEX IF NOT EXISTS idx_pod_registration_expires_at
    ON tshepo.pod_registration (expires_at);
