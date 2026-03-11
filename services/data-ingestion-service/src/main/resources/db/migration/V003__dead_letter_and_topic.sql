-- ============================================================================
-- data-ingestion-service V003 — Dead letter table + topic_name on bronze
-- ============================================================================

-- Add topic_name column to bronze events (captures source Kafka topic)
ALTER TABLE din_bronze_event ADD COLUMN topic_name VARCHAR(255);

CREATE INDEX idx_din_bronze_topic ON din_bronze_event (topic_name);

-- Dead letter table for poison messages that fail deserialization or validation
CREATE TABLE din_dead_letter_event (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID,
    pod_id              VARCHAR(64),
    topic_name          VARCHAR(255)    NOT NULL,
    partition_num       INT             NOT NULL DEFAULT 0,
    offset_num          BIGINT          NOT NULL DEFAULT 0,
    key_bytes           TEXT,
    value_bytes         TEXT            NOT NULL,
    error_message       TEXT            NOT NULL,
    error_class         VARCHAR(512),
    retry_count         INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_din_dead_letter_topic ON din_dead_letter_event (topic_name);
CREATE INDEX idx_din_dead_letter_created ON din_dead_letter_event (created_at);
