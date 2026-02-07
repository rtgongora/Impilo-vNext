-- =============================================================================
-- BUTANO — Custom tables for SHR management
-- HAPI FHIR JPA manages its own tables via Hibernate DDL-auto.
-- These tables support reconciliation, tenant registry, and outbox eventing.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- Tenant registry: tracks active tenants and their configuration
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS butano_tenant_registry (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL UNIQUE,
    tenant_name     VARCHAR(255) NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_registry_tenant_id ON butano_tenant_registry (tenant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Reconciliation mapping: O-CPID → CPID rewrite tracking
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS butano_reconciliation_mapping (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    old_cpid        VARCHAR(128) NOT NULL,
    new_cpid        VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    resources_updated INT       NOT NULL DEFAULT 0,
    requested_by    VARCHAR(128) NOT NULL,
    correlation_id  VARCHAR(128),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,

    CONSTRAINT uq_reconciliation_mapping UNIQUE (tenant_id, old_cpid)
);

CREATE INDEX idx_reconciliation_tenant_status ON butano_reconciliation_mapping (tenant_id, status);
CREATE INDEX idx_reconciliation_old_cpid ON butano_reconciliation_mapping (old_cpid);

-- ─────────────────────────────────────────────────────────────────────────────
-- Event outbox: reliable Kafka publishing via transactional outbox pattern
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS butano_event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(128) NOT NULL,
    aggregate_id    VARCHAR(128) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB       NOT NULL,
    tenant_id       UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON butano_event_outbox (published_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_tenant ON butano_event_outbox (tenant_id, created_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- CPID lookup index hint (for HAPI FHIR tables)
-- These will be applied after HAPI creates its tables; idempotent.
-- ─────────────────────────────────────────────────────────────────────────────
-- Note: HAPI FHIR automatically creates indexes on identifier columns.
-- These additional indexes are for common query patterns:
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_hfj_res_tag_tenant
--   ON hfj_res_tag (tag_id) WHERE tag_id IS NOT NULL;
