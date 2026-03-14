-- iot-ingestion-service: telemetry store (append-only) + DLQ
-- Table prefix: iot_

-- Append-only telemetry readings from devices
CREATE TABLE iot_telemetry_readings (
    id              BIGSERIAL       PRIMARY KEY,
    reading_id      UUID            NOT NULL DEFAULT gen_random_uuid(),
    device_id       VARCHAR(255)    NOT NULL,
    tenant_id       UUID            NOT NULL,
    metric_type     VARCHAR(128)    NOT NULL,
    metric_value    DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(32),
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1',
    recorded_at     TIMESTAMPTZ     NOT NULL,
    ingested_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    source          VARCHAR(32)     NOT NULL DEFAULT 'HTTP',
    metadata_json   TEXT,
    CONSTRAINT uq_iot_reading UNIQUE (reading_id)
);

CREATE INDEX idx_iot_readings_device ON iot_telemetry_readings (device_id, recorded_at DESC);
CREATE INDEX idx_iot_readings_tenant ON iot_telemetry_readings (tenant_id, ingested_at DESC);
CREATE INDEX idx_iot_readings_metric ON iot_telemetry_readings (metric_type, recorded_at DESC);

-- Dead letter queue for invalid telemetry payloads
CREATE TABLE iot_telemetry_dlq (
    id              BIGSERIAL       PRIMARY KEY,
    dlq_id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    device_id       VARCHAR(255),
    tenant_id       UUID,
    raw_payload     TEXT            NOT NULL,
    error_code      VARCHAR(64)     NOT NULL,
    error_message   TEXT            NOT NULL,
    source          VARCHAR(32)     NOT NULL DEFAULT 'HTTP',
    schema_version  VARCHAR(16),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_iot_dlq UNIQUE (dlq_id)
);

CREATE INDEX idx_iot_dlq_device ON iot_telemetry_dlq (device_id, created_at DESC);
CREATE INDEX idx_iot_dlq_error ON iot_telemetry_dlq (error_code, created_at DESC);
