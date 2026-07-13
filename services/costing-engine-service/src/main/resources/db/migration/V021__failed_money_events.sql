-- Dead-letter store for money-critical Kafka events.
--
-- Every money consumer used to catch → log → ack, which permanently dropped a
-- settlement/refund/charge event on any transient failure (a lost
-- mushex.payment.status.changed left a bill unpaid forever with no recovery).
-- Failures now retry with bounded backoff and then land on the
-- costa.money.dlq topic; this table is the durable, queryable ops surface for
-- those dead letters, and replay re-publishes the original payload onto the
-- original topic (safe: every money consumer is idempotent).

CREATE TABLE IF NOT EXISTS costa_failed_money_events (
    id              BIGSERIAL PRIMARY KEY,
    original_topic  VARCHAR(200) NOT NULL,
    payload         TEXT         NOT NULL,
    error_message   TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DEAD',  -- DEAD | REPLAYED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    replayed_at     TIMESTAMPTZ,
    replayed_by     VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_failed_money_events_status
    ON costa_failed_money_events (status, created_at);
