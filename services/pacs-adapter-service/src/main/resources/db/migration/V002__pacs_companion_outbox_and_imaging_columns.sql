-- Align pacs.event_outbox with CompanionOutboxPublisher (published_at + aggregate routing).
-- Extend imaging_study for OROS correlation and accession matching.

ALTER TABLE pacs.imaging_study
    ADD COLUMN IF NOT EXISTS oros_order_id VARCHAR(26),
    ADD COLUMN IF NOT EXISTS accession_number VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_pacs_study_oros_order
    ON pacs.imaging_study (tenant_id, oros_order_id)
    WHERE oros_order_id IS NOT NULL;

DROP INDEX IF EXISTS pacs.idx_pacs_outbox_unpublished;
DROP TABLE IF EXISTS pacs.event_outbox;

CREATE TABLE pacs.event_outbox (
    id               BIGSERIAL       PRIMARY KEY,
    aggregate_type   VARCHAR(50)     NOT NULL,
    aggregate_id     VARCHAR(255)    NOT NULL,
    event_type       VARCHAR(100)    NOT NULL,
    payload          TEXT            NOT NULL,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    tenant_id        TEXT,
    pod_id           TEXT,
    correlation_id   TEXT,
    causation_id     TEXT,
    idempotency_key  TEXT,
    schema_version   INT             DEFAULT 1,
    producer         VARCHAR(64)     DEFAULT 'pacs-adapter',
    subject_type     VARCHAR(64),
    subject_id       VARCHAR(255),
    partition_key    VARCHAR(255),
    occurred_at      TIMESTAMPTZ,
    publish_error    TEXT
);

CREATE INDEX idx_pacs_outbox_unpublished ON pacs.event_outbox (created_at)
    WHERE published_at IS NULL;
