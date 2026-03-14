-- Wave 8: Control channel watermark table for idempotent event processing
CREATE TABLE IF NOT EXISTS vito.control_channel_watermark (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL UNIQUE,
    event_type      VARCHAR(64) NOT NULL,
    subject_id      VARCHAR(128),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  VARCHAR(64)
);

CREATE INDEX idx_ccw_event_id ON vito.control_channel_watermark (event_id);
CREATE INDEX idx_ccw_subject_id ON vito.control_channel_watermark (subject_id);

COMMENT ON TABLE vito.control_channel_watermark IS 'Tracks processed high-priority control channel events for idempotency';
