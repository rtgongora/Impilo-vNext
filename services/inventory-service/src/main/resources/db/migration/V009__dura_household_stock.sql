-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V009__dura_household_stock.sql
-- Prefix  : inv_
--
-- Dura Wave: client / caregiver household stock and refill requests.
--
-- Household stock is personal health data — access is governed by Tshepo/Vito
-- (client self or authorised caregiver). Dura records the client context and,
-- where relevant, the managing caregiver.
-- =============================================

CREATE TABLE inv_client_home_stock (
    home_stock_id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL,
    client_id            VARCHAR(64)   NOT NULL,
    item_code            VARCHAR(50),
    item_name            VARCHAR(255)  NOT NULL,
    qty                  INTEGER       NOT NULL DEFAULT 0,
    uom                  VARCHAR(20),
    batch_number         VARCHAR(100),
    expiry_date          DATE,
    source               VARCHAR(20)   NOT NULL DEFAULT 'SELF',
    managed_by_caregiver VARCHAR(64),
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    notes                VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_home_stock_client   ON inv_client_home_stock(tenant_id, client_id, status);
CREATE INDEX idx_home_stock_expiry   ON inv_client_home_stock(tenant_id, expiry_date) WHERE expiry_date IS NOT NULL;

CREATE TABLE inv_refill_requests (
    refill_id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL,
    client_id            VARCHAR(64)   NOT NULL,
    item_code            VARCHAR(50),
    item_name            VARCHAR(255)  NOT NULL,
    qty_requested        INTEGER       NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED',
    preferred_facility_id UUID,
    requested_by         VARCHAR(64),
    notes                VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fulfilled_at         TIMESTAMPTZ
);

CREATE INDEX idx_refills_client ON inv_refill_requests(tenant_id, client_id, status);
