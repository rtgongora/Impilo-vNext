-- VITO — Client Registry: Base schema

CREATE SCHEMA IF NOT EXISTS vito;

CREATE TABLE vito.client (
    id              BIGSERIAL PRIMARY KEY,
    health_id       UUID         NOT NULL UNIQUE,
    tenant_id       UUID         NOT NULL,
    given_name      VARCHAR(255),
    family_name     VARCHAR(255),
    date_of_birth   DATE,
    sex             VARCHAR(10),
    phone_hash      VARCHAR(64),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_client_tenant ON vito.client (tenant_id, status);
