-- =============================================
-- Service : varapi-service
-- Schema  : varapi
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS varapi;

CREATE TABLE varapi.provider (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    provider_ref    UUID            NOT NULL UNIQUE,
    given_name      VARCHAR(255),
    family_name     VARCHAR(255),
    practice_number VARCHAR(100),
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     DEFAULT now(),
    updated_at      TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_provider_tenant_status ON varapi.provider (tenant_id, status);
