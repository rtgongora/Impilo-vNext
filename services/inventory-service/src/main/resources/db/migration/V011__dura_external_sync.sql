-- =============================================
-- Service : inventory-service  (Dura — sovereign stock brain)
-- Database: inventory
-- Flyway  : V011__dura_external_sync.sql
-- Prefix  : inv_
--
-- Dura Wave: eLMIS/NatPharm external sync state.
--
-- Dura remains functional without eLMIS. Sync state is tracked SEPARATELY from
-- native stock truth — a sync failure NEVER mutates the ledger or on-hand. State
-- is explicit (PENDING/SYNCED/FAILED/RETRY), auditable (error history), and
-- retryable (replay back to PENDING).
-- =============================================

CREATE TABLE inv_external_sync_state (
    sync_id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    external_system VARCHAR(20)   NOT NULL,
    entity_type     VARCHAR(30)   NOT NULL,
    entity_ref      VARCHAR(100)  NOT NULL,
    external_ref    VARCHAR(100),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    payload         JSONB,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    synced_at       TIMESTAMPTZ
);

CREATE INDEX idx_sync_state_status ON inv_external_sync_state(tenant_id, status);
CREATE INDEX idx_sync_state_entity ON inv_external_sync_state(tenant_id, entity_type, entity_ref);

CREATE TABLE inv_external_sync_errors (
    error_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    sync_id        UUID          NOT NULL REFERENCES inv_external_sync_state(sync_id),
    attempt_number INTEGER       NOT NULL,
    error_message  VARCHAR(1000),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_sync_errors_sync ON inv_external_sync_errors(sync_id);
