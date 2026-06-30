-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V008__dura_cold_chain.sql
-- Prefix  : inv_
--
-- Dura Wave: cold-chain. Cold-chain locations (fridge/cold-room/freezer),
-- temperature logs (manual or IoT), and excursions raised automatically when a
-- log falls outside the location's safe range.
-- =============================================

CREATE TABLE inv_cold_chain_locations (
    cc_location_id UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    facility_id    UUID          NOT NULL,
    store_id       UUID,
    name           VARCHAR(255)  NOT NULL,
    type           VARCHAR(20)   NOT NULL DEFAULT 'FRIDGE',
    temp_min       DOUBLE PRECISION NOT NULL,
    temp_max       DOUBLE PRECISION NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_cc_locations_tenant_facility ON inv_cold_chain_locations(tenant_id, facility_id);

CREATE TABLE inv_temperature_logs (
    log_id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    cc_location_id UUID          NOT NULL REFERENCES inv_cold_chain_locations(cc_location_id),
    temperature    DOUBLE PRECISION NOT NULL,
    source         VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    within_range   BOOLEAN       NOT NULL,
    recorded_by    VARCHAR(100),
    recorded_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_temp_logs_location ON inv_temperature_logs(tenant_id, cc_location_id, recorded_at);

CREATE TABLE inv_cold_chain_excursions (
    excursion_id   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    cc_location_id UUID          NOT NULL REFERENCES inv_cold_chain_locations(cc_location_id),
    log_id         UUID          REFERENCES inv_temperature_logs(log_id),
    temperature    DOUBLE PRECISION NOT NULL,
    breach         VARCHAR(10)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    resolution     VARCHAR(20),
    notes          VARCHAR(1000),
    resolved_by    VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ
);

CREATE INDEX idx_cc_excursions_tenant_status ON inv_cold_chain_excursions(tenant_id, status);
CREATE INDEX idx_cc_excursions_location      ON inv_cold_chain_excursions(cc_location_id);
