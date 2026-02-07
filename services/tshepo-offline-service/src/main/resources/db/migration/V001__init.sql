-- TSHEPO Offline — Offline Trust Controls: Base schema
-- Stores capability tokens, offline action logs, offline packs,
-- reconciliation batches, and event outbox for reliable Kafka publishing.

CREATE SCHEMA IF NOT EXISTS tshepo_offline;

-- ============================================================
-- Capability Token — facility-scoped, short-TTL offline tokens
-- ============================================================
CREATE TABLE tshepo_offline.capability_token (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    actor_id            VARCHAR(255)    NOT NULL,
    facility_id         UUID            NOT NULL,
    device_fingerprint  VARCHAR(255)    NOT NULL,
    capabilities        JSONB           NOT NULL,
    issued_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ     NOT NULL,
    revoked_at          TIMESTAMPTZ,
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_cap_token_tenant_actor
    ON tshepo_offline.capability_token (tenant_id, actor_id);
CREATE INDEX idx_cap_token_tenant_facility
    ON tshepo_offline.capability_token (tenant_id, facility_id);
CREATE INDEX idx_cap_token_status_expires
    ON tshepo_offline.capability_token (status, expires_at);

-- ============================================================
-- Offline Action Log — every action performed while offline
-- ============================================================
CREATE TABLE tshepo_offline.offline_action_log (
    id                      BIGSERIAL       PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    capability_token_id     UUID            NOT NULL REFERENCES tshepo_offline.capability_token(id),
    actor_id                VARCHAR(255)    NOT NULL,
    action_type             VARCHAR(64)     NOT NULL,
    resource_type           VARCHAR(64),
    resource_ref            VARCHAR(255),
    o_cpid                  UUID,
    payload                 JSONB,
    performed_at            TIMESTAMPTZ     NOT NULL,
    synced_at               TIMESTAMPTZ,
    sync_status             VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_action_log_tenant_token
    ON tshepo_offline.offline_action_log (tenant_id, capability_token_id);
CREATE INDEX idx_action_log_sync_status
    ON tshepo_offline.offline_action_log (sync_status);
CREATE INDEX idx_action_log_tenant_actor
    ON tshepo_offline.offline_action_log (tenant_id, actor_id, performed_at DESC);

-- ============================================================
-- Offline Pack — pre-cached policy + consent + terminology
-- ============================================================
CREATE TABLE tshepo_offline.offline_pack (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    facility_id             UUID            NOT NULL,
    generated_for           VARCHAR(255)    NOT NULL,
    policy_snapshot         JSONB           NOT NULL,
    consent_snapshot        JSONB,
    terminology_snapshot    JSONB,
    generated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ     NOT NULL,
    version                 INT             NOT NULL DEFAULT 1
);

CREATE INDEX idx_offline_pack_tenant_facility
    ON tshepo_offline.offline_pack (tenant_id, facility_id, generated_at DESC);

-- ============================================================
-- Reconciliation Batch — tracks sync-back processing
-- ============================================================
CREATE TABLE tshepo_offline.reconciliation_batch (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    facility_id         UUID            NOT NULL,
    submitted_by        VARCHAR(255)    NOT NULL,
    action_count        INT             NOT NULL,
    reconciled_count    INT             NOT NULL DEFAULT 0,
    failed_count        INT             NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'SUBMITTED',
    submitted_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_recon_batch_tenant_status
    ON tshepo_offline.reconciliation_batch (tenant_id, status);
CREATE INDEX idx_recon_batch_facility
    ON tshepo_offline.reconciliation_batch (tenant_id, facility_id, submitted_at DESC);

-- ============================================================
-- Event Outbox — transactional outbox for reliable Kafka delivery
-- ============================================================
CREATE TABLE tshepo_offline.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_event_outbox_unpublished
    ON tshepo_offline.event_outbox (created_at) WHERE published_at IS NULL;
