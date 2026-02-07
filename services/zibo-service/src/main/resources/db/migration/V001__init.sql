-- =============================================
-- Service : zibo-service
-- Schema  : zibo
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS zibo;

CREATE TABLE zibo.code_system (
    id          BIGSERIAL       PRIMARY KEY,
    tenant_id   UUID            NOT NULL,
    uri         VARCHAR(500)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    version     VARCHAR(50)     NOT NULL,
    status      VARCHAR(20)     DEFAULT 'ACTIVE',
    content     JSONB,
    created_at  TIMESTAMPTZ     DEFAULT now(),
    UNIQUE (uri, version)
);
