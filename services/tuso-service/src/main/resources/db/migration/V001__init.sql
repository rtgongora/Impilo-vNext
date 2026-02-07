-- =============================================
-- Service : tuso-service
-- Schema  : tuso
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS tuso;

CREATE TABLE tuso.facility (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_code   VARCHAR(50)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    facility_type   VARCHAR(50),
    province        VARCHAR(100),
    district        VARCHAR(100),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (tenant_id, facility_code)
);

CREATE INDEX idx_facility_tenant_status ON tuso.facility (tenant_id, status);
