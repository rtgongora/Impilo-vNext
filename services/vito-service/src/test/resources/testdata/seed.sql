-- =============================================================================
-- VITO test seed data (H2-compatible)
-- =============================================================================
-- This file runs AFTER Hibernate ddl-auto:create-drop creates JPA-managed
-- tables (client, event_outbox, etc.).
--
-- It creates NON-JPA tables (idempotency_keys) that Flyway would normally
-- create but which are skipped in the H2 test profile, and inserts minimal
-- baseline rows for V1.1 merge/idempotency/outbox integration tests.
-- =============================================================================

-- -------------------------------------------------------------------------
-- idempotency_keys — NOT a JPA entity; created by V016 Flyway migration
-- in production. Must be created here for H2 test profile.
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_keys (
    tenant_id       VARCHAR(255) NOT NULL,
    pod_id          VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(512) NOT NULL,
    response_status INT          NOT NULL,
    response_body   CLOB         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP    NULL,
    PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- -------------------------------------------------------------------------
-- Baseline client records for merge tests.
-- V11MergeFlowIT creates its own clients in @BeforeEach, but these
-- provide a known-good baseline to verify schema + seed wiring works.
-- -------------------------------------------------------------------------
INSERT INTO client (tenant_id, crid, health_id, given_name, family_name, status, date_of_birth, sex, created_at, updated_at)
VALUES
    ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'aaaaaaaa-1111-2222-3333-444444444444',
     'bbbbbbbb-1111-2222-3333-444444444444', 'Seed', 'Survivor', 'ACTIVE',
     DATE '1990-01-15', 'M', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'cccccccc-1111-2222-3333-444444444444',
     'dddddddd-1111-2222-3333-444444444444', 'Seed', 'MergeTarget', 'ACTIVE',
     DATE '1992-06-20', 'F', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
