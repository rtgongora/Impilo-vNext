-- ============================================================================
-- asset-registry-service V007 — IoT threshold-breach derivation (WS#5 GAP-2)
--
-- WS#5 derived only OFFLINE/heartbeat alerts. This adds the configuration + runtime
-- state needed to derive THRESHOLD_BREACH alerts (e.g. cold-chain temperature) from
-- the REAL metric values already ingested by iot-ingestion-service and published on
-- telemetry.iot.device.reading (event impilo.iot.telemetry.reading.ingested.v1, payload
-- {device_id, metric_type, metric_value, unit, recorded_at}). No telemetry is fabricated —
-- derivation reads only real ingested metricValue. The alert itself lands in the existing
-- asr_iot_alert table (alert_type THRESHOLD_BREACH, metric_type, threshold_value) via the
-- existing EquipmentOperationsService.raiseAlert dedupe + lifecycle-event + outbox path.
--
-- Boundary: cold-chain environmental breaches route to Madi/public-health (route_to);
-- the asset domain holds only the operational fridge/device status (discovery §3).
-- ============================================================================

-- ── Threshold rule configuration: how a metric breaches and where the alert routes ──
CREATE TABLE asr_iot_threshold_rule (
    rule_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    metric_type     VARCHAR(128)    NOT NULL,                 -- e.g. temperature_c, humidity_pct
    comparison      VARCHAR(8)      NOT NULL,                 -- GT | GTE | LT | LTE  (breach when reading <op> threshold)
    threshold_value DOUBLE PRECISION NOT NULL,
    min_count       INT             NOT NULL DEFAULT 1,       -- consecutive breaching readings before alerting
    severity        VARCHAR(16)     NOT NULL DEFAULT 'WARN',  -- INFO | WARN | CRITICAL
    route_to        VARCHAR(64),                              -- MADI | PUBLIC_HEALTH | INVENTORY | NULL (operational only)
    equipment_type  VARCHAR(64),                              -- optional narrowing (e.g. FRIDGE); NULL = any
    label           VARCHAR(256),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asr_threshold_rule UNIQUE (tenant_id, metric_type, comparison, threshold_value)
);
CREATE INDEX idx_asr_threshold_rule_metric ON asr_iot_threshold_rule (tenant_id, metric_type) WHERE active = TRUE;

-- ── Consecutive-breach streak state per (tenant, device, rule). Reset on a within-threshold
--    reading; alert is raised once the streak reaches the rule's min_count. ─────────────────
CREATE TABLE asr_iot_breach_state (
    state_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    device_id       VARCHAR(255)    NOT NULL,
    rule_id         UUID            NOT NULL REFERENCES asr_iot_threshold_rule(rule_id) ON DELETE CASCADE,
    consecutive     INT             NOT NULL DEFAULT 0,
    last_value      DOUBLE PRECISION,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asr_breach_state UNIQUE (tenant_id, device_id, rule_id)
);
CREATE INDEX idx_asr_breach_state_device ON asr_iot_breach_state (tenant_id, device_id);

-- ── Representative cold-chain rule: a vaccine/blood fridge breaches above 8°C; alert after 3
--    consecutive readings (debounces a single noisy spike), CRITICAL, routed to Madi. ───────
INSERT INTO asr_iot_threshold_rule
    (tenant_id, metric_type, comparison, threshold_value, min_count, severity, route_to, equipment_type, label)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'temperature_c', 'GT', 8.0, 3, 'CRITICAL', 'MADI', 'FRIDGE',
     'Cold-chain fridge over 8C (3 consecutive readings)');
