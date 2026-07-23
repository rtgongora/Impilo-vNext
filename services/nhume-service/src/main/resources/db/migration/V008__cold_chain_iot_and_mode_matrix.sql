-- ============================================================================
-- nhume-service V008 — OF-B19 cold-chain IoT + OF-B20 mode-matrix enablement
--
-- OF-B19 (§12.6): cold-chain deliveries bind a telemetry sensor DeviceId and a
-- cargo-specific temperature band. Excursions are UNSUPPRESSIBLE — every band
-- breach is an append-only TEMPERATURE_EXCURSION custody event AND flips
-- excursion_flagged; the flag can only be resolved by a recorded pharmacist
-- stability decision (USE => releasable, QUARANTINE => RETURNED per §12.7 and
-- the msika-flow refund seam). Handover of a flagged cold delivery without a
-- decision is refused fail-closed in code.
--
-- OF-B20 (§12.9): nhume_mode_corridors is the GOVERNED transport-mode matrix.
-- HONESTY LAW (CONFIG-ONLY — R67/OF-G21): corridors ship DISABLED with no
-- evidence; enabling one is an ops/governance act that must attach the §12.9
-- evidence pack (evidence_ref). NO code path may ever record a mission as
-- flown — actual flight ops are outside the estate; missions exist so the
-- capability is auditable and the ROAD fallback is real (journey #67).
-- ============================================================================

ALTER TABLE nhume_delivery_requests
    ADD COLUMN sensor_device_ref        VARCHAR(128),
    ADD COLUMN cold_min_c               NUMERIC(6,2),
    ADD COLUMN cold_max_c               NUMERIC(6,2),
    ADD COLUMN excursion_flagged        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stability_decision       VARCHAR(16),
    ADD COLUMN stability_decision_by    VARCHAR(255),
    ADD COLUMN stability_decision_at    TIMESTAMPTZ,
    ADD COLUMN stability_decision_notes TEXT,
    ADD COLUMN delivery_mode            VARCHAR(32),
    ADD COLUMN mode_fallback_reason     VARCHAR(255);

-- IoT-bus fan-in: find the in-transit cold deliveries a sensor reading belongs to.
CREATE INDEX idx_nhume_delivery_sensor_ref
    ON nhume_delivery_requests (sensor_device_ref)
    WHERE sensor_device_ref IS NOT NULL;

-- Ops queue: excursion-flagged deliveries awaiting a stability decision.
CREATE INDEX idx_nhume_delivery_excursion
    ON nhume_delivery_requests (tenant_id)
    WHERE excursion_flagged = TRUE;

-- §12.9 governed transport-mode corridor matrix. A corridor is one
-- (geography, mode) capability row with its eligibility constraints.
-- enabled defaults FALSE and weather_status defaults 'UNKNOWN' — the drone
-- dispatch gate passes ONLY on enabled = TRUE AND weather_status = 'CLEAR'
-- (fail-closed: unknown weather is a governed fallback, never a guess).
CREATE TABLE nhume_mode_corridors (
    corridor_id        UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    corridor_code      VARCHAR(64)  NOT NULL,
    display_name       VARCHAR(255),
    mode               VARCHAR(32)  NOT NULL,               -- DeliveryMode name (DRONE, ...)
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE, -- evidence-gated governance act
    max_payload_grams  NUMERIC(10,0),
    max_distance_km    NUMERIC(8,2),
    cold_chain_allowed BOOLEAN      NOT NULL DEFAULT FALSE, -- certified payload bay only (§12.9)
    controlled_allowed BOOLEAN      NOT NULL DEFAULT FALSE,
    weather_status     VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN', -- CLEAR | BLOCKED | UNKNOWN
    evidence_ref       VARCHAR(255),                        -- §12.9 evidence pack reference
    notes              TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_nhume_corridor UNIQUE (tenant_id, corridor_code)
);

-- No seed rows: an enabled corridor without an evidence pack would be a
-- configuration lie (CC-20 honesty rule). Corridors are created by governance.

-- Mission record gains the corridor linkage and the governed-fallback reason
-- (journey #67 — mission retained on weather fallback, never claimed flown).
ALTER TABLE nhume_autonomous_missions
    ADD COLUMN corridor_code   VARCHAR(64),
    ADD COLUMN fallback_reason VARCHAR(255);
