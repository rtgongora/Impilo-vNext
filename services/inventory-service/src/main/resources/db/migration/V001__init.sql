-- =============================================
-- Service : inventory-service
-- Schema  : inventory
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.stock_item (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    facility_id         UUID            NOT NULL,
    product_code        VARCHAR(100)    NOT NULL,
    batch_number        VARCHAR(100),
    quantity_on_hand    NUMERIC(12,2)   NOT NULL DEFAULT 0,
    expiry_date         DATE,
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (tenant_id, facility_id, product_code, batch_number)
);
