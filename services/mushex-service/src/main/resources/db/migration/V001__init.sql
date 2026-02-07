-- =============================================
-- Service : mushex-service
-- Schema  : mushex
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS mushex;

CREATE TABLE mushex.payment_intent (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    payment_ref         UUID            NOT NULL UNIQUE,
    idempotency_key     VARCHAR(128)    NOT NULL UNIQUE,
    encounter_id        UUID,
    subject_cpid        VARCHAR(64),
    amount              NUMERIC(12,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status              VARCHAR(30)     DEFAULT 'CREATED',
    provider            VARCHAR(50),
    callback_url        TEXT,
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX idx_payment_intent_tenant_status ON mushex.payment_intent (tenant_id, status);
