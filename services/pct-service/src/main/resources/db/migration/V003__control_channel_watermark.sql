-- Wave 8: Control channel watermark table for idempotent event processing
CREATE TABLE IF NOT EXISTS pct.control_channel_watermark (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL UNIQUE,
    event_type      VARCHAR(64) NOT NULL,
    subject_id      VARCHAR(128),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id  VARCHAR(64)
);

CREATE INDEX idx_pct_ccw_event_id ON pct.control_channel_watermark (event_id);
CREATE INDEX idx_pct_ccw_subject_id ON pct.control_channel_watermark (subject_id);

COMMENT ON TABLE pct.control_channel_watermark IS 'Tracks processed high-priority control channel events for idempotency';
