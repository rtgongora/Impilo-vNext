-- ============================================================
-- Search Service  (v1.1-native)
-- V001: Base schema
-- ============================================================

-- ----------------------------------------------------------
-- 1. ss_search_index — indexed entity content for search
-- ----------------------------------------------------------
CREATE TABLE ss_search_index (
    id              VARCHAR(36)     PRIMARY KEY,
    entity_type     VARCHAR(100)    NOT NULL,
    entity_id       VARCHAR(255)    NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    pod_id          VARCHAR(255),
    content_json    TEXT            NOT NULL,
    searchable_text TEXT            NOT NULL,
    tags            VARCHAR(1000),
    indexed_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    source_event    VARCHAR(255),

    CONSTRAINT uq_ss_entity_tenant UNIQUE (entity_type, entity_id, tenant_id)
);

CREATE INDEX idx_ss_search_index_tenant ON ss_search_index (tenant_id);
CREATE INDEX idx_ss_search_index_tenant_type ON ss_search_index (tenant_id, entity_type);
CREATE INDEX idx_ss_search_index_searchable ON ss_search_index (tenant_id, searchable_text);
-- For PostgreSQL with pg_trgm extension, consider:
-- CREATE INDEX idx_ss_search_index_trgm ON ss_search_index USING GIN (searchable_text gin_trgm_ops);

-- ----------------------------------------------------------
-- 2. ss_event_outbox — transactional outbox for Kafka
-- ----------------------------------------------------------
CREATE TABLE ss_event_outbox (
    id              VARCHAR(36)     PRIMARY KEY,
    tenant_id       VARCHAR(255)    NOT NULL,
    pod_id          VARCHAR(255)    NOT NULL,
    correlation_id  VARCHAR(255)    NOT NULL,
    idempotency_key VARCHAR(255),
    event_type      VARCHAR(256)    NOT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    payload_json    TEXT            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ss_outbox_unpublished
    ON ss_event_outbox (created_at) WHERE published_at IS NULL;

-- ----------------------------------------------------------
-- 3. idempotency_keys — request dedup
-- ----------------------------------------------------------
CREATE TABLE idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INT             NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ss_idempotency_key ON idempotency_keys (idempotency_key);
