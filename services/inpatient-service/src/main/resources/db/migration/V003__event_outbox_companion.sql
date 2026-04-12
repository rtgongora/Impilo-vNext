-- Align inpatient.event_outbox with CompanionOutboxPublisher (bigint id + aggregate routing).

DROP INDEX IF EXISTS inpatient.idx_inpatient_outbox_unpublished;
DROP TABLE IF EXISTS inpatient.event_outbox;

CREATE TABLE inpatient.event_outbox (
    id                 BIGSERIAL       PRIMARY KEY,
    aggregate_type     VARCHAR(64)     NOT NULL,
    aggregate_id       VARCHAR(256)    NOT NULL,
    tenant_id          VARCHAR(64)     NOT NULL,
    pod_id             VARCHAR(64)     NOT NULL,
    correlation_id     VARCHAR(64)     NOT NULL,
    idempotency_key    VARCHAR(128),
    event_type         VARCHAR(256)    NOT NULL,
    schema_version     INT             NOT NULL DEFAULT 1,
    occurred_at        TIMESTAMPTZ     NOT NULL,
    payload_json       TEXT            NOT NULL,
    published_at       TIMESTAMPTZ,
    publish_error      TEXT,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_inpatient_outbox_unpublished ON inpatient.event_outbox (created_at)
    WHERE published_at IS NULL;
