-- =============================================
-- Service : product-registry-service
-- Schema  : product_registry
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS product_registry;

CREATE TABLE product_registry.product (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    product_code    VARCHAR(100)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    category        VARCHAR(100),
    unit_of_measure VARCHAR(50),
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (tenant_id, product_code)
);
