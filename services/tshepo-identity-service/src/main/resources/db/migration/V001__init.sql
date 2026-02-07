-- TSHEPO Identity — Identity Resolution & Tokenisation: Base schema
-- Stores opaque identifier mappings, provisional CPIDs, MOSIP encrypted links,
-- scoped tokens, and the event outbox. NO PII is stored in any table.

CREATE SCHEMA IF NOT EXISTS tshepo_identity;

-- ============================================================================
-- 1. ID Mapping: Health ID ↔ CPID (canonical patient identifier)
-- ============================================================================
CREATE TABLE tshepo_identity.id_mapping (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    health_id       UUID            NOT NULL,
    cpid            UUID            NOT NULL UNIQUE,
    crid            UUID,
    mapping_status  VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

-- Each (tenant, health_id) pair must map to exactly one CPID
CREATE UNIQUE INDEX uq_id_mapping_tenant_health ON tshepo_identity.id_mapping (tenant_id, health_id);
CREATE INDEX idx_id_mapping_cpid ON tshepo_identity.id_mapping (cpid);

-- ============================================================================
-- 2. Provisional CPID (O-CPID): Issued offline, reconciled later
-- ============================================================================
CREATE TABLE tshepo_identity.provisional_cpid (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    o_cpid              UUID            NOT NULL UNIQUE,
    facility_id         UUID            NOT NULL,
    device_fingerprint  VARCHAR(255)    NOT NULL,
    issued_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reconciled_at       TIMESTAMPTZ,
    canonical_cpid      UUID,
    status              VARCHAR(16)     NOT NULL DEFAULT 'PROVISIONAL'
);

CREATE INDEX idx_provisional_tenant_status ON tshepo_identity.provisional_cpid (tenant_id, status);

-- ============================================================================
-- 3. MOSIP Link: Encrypted, indirect reference to national identity system
-- ============================================================================
CREATE TABLE tshepo_identity.mosip_link (
    id                      BIGSERIAL       PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    health_id               UUID            NOT NULL,
    link_ref_hash           VARCHAR(128)    NOT NULL,
    link_ref_encrypted      BYTEA           NOT NULL,
    verification_status     VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    linked_at               TIMESTAMPTZ,
    proofing_event_ref      VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_mosip_link_hash ON tshepo_identity.mosip_link (link_ref_hash);
CREATE INDEX idx_mosip_link_tenant_health ON tshepo_identity.mosip_link (tenant_id, health_id);

-- ============================================================================
-- 4. Scoped Token: Short-lived, service-scoped JWS tokens for downstream calls
-- ============================================================================
CREATE TABLE tshepo_identity.scoped_token (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    target_service  VARCHAR(128)    NOT NULL,
    scope           VARCHAR(255)    NOT NULL,
    subject_ref     VARCHAR(255),
    jti             VARCHAR(64)     NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_scoped_token_tenant_actor ON tshepo_identity.scoped_token (tenant_id, actor_id);
CREATE INDEX idx_scoped_token_expires ON tshepo_identity.scoped_token (expires_at) WHERE status = 'ACTIVE';

-- ============================================================================
-- 5. Event Outbox: Transactional outbox for reliable Kafka publishing
-- ============================================================================
CREATE TABLE tshepo_identity.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_event_outbox_unpublished ON tshepo_identity.event_outbox (created_at ASC) WHERE published_at IS NULL;
