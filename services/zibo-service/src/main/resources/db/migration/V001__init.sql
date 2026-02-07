-- =====================================================================
-- Service : zibo-service
-- Database: zibo (public schema, zibo_ prefix)
-- Flyway  : V001__init.sql
-- Purpose : Foundation tables for the ZIBO national terminology,
--           definitions & classification service.
-- =====================================================================

-- ─────────────────────────────────────────────────────────────────────
-- 1. zibo_artifacts — stores all FHIR terminology & conformance resources
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_artifacts (
    artifact_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    fhir_type       VARCHAR(30)     NOT NULL,       -- CODE_SYSTEM, VALUE_SET, CONCEPT_MAP, etc.
    canonical_url   VARCHAR(500)    NOT NULL,
    version         VARCHAR(50)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    title           VARCHAR(500),
    description     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    content_json    JSONB           NOT NULL,
    content_hash    VARCHAR(64)     NOT NULL,       -- SHA-256 of content for dedup
    publisher       VARCHAR(255),
    jurisdiction    VARCHAR(10)     DEFAULT 'ZW',
    created_by      VARCHAR(128)    NOT NULL,
    published_by    VARCHAR(128),
    published_at    TIMESTAMPTZ,
    deprecated_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, canonical_url, version)
);

-- ─────────────────────────────────────────────────────────────────────
-- 2. zibo_packs — terminology pack bundles
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_packs (
    pack_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    version         VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    manifest_json   JSONB,                          -- metadata about the pack
    created_by      VARCHAR(128)    NOT NULL,
    published_by    VARCHAR(128),
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name, version)
);

-- ─────────────────────────────────────────────────────────────────────
-- 3. zibo_pack_artifacts — join table (pack ↔ artifact)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_pack_artifacts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id         UUID            NOT NULL REFERENCES zibo_packs(pack_id),
    artifact_id     UUID            NOT NULL REFERENCES zibo_artifacts(artifact_id),
    UNIQUE (pack_id, artifact_id)
);

-- ─────────────────────────────────────────────────────────────────────
-- 4. zibo_assignments — pack assignments to scopes
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_assignments (
    assignment_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    scope_type      VARCHAR(20)     NOT NULL,       -- TENANT, FACILITY, WORKSPACE
    scope_id        UUID            NOT NULL,
    pack_id         UUID            NOT NULL REFERENCES zibo_packs(pack_id),
    pack_version    VARCHAR(50)     NOT NULL,
    policy_mode     VARCHAR(10)     NOT NULL DEFAULT 'LENIENT',  -- STRICT, LENIENT
    active          BOOLEAN         NOT NULL DEFAULT true,
    assigned_by     VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, scope_type, scope_id, pack_id)
);

-- ─────────────────────────────────────────────────────────────────────
-- 5. zibo_mappings_index — flattened concept map index for fast lookup
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_mappings_index (
    mapping_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    source_system   VARCHAR(500)    NOT NULL,
    source_code     VARCHAR(100)    NOT NULL,
    source_display  VARCHAR(500),
    target_system   VARCHAR(500)    NOT NULL,
    target_code     VARCHAR(100)    NOT NULL,
    target_display  VARCHAR(500),
    equivalence     VARCHAR(30)     NOT NULL DEFAULT 'equivalent',
    conceptmap_ref  UUID            REFERENCES zibo_artifacts(artifact_id),
    confidence      NUMERIC(3,2)    DEFAULT 1.00,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────
-- 6. zibo_validation_jobs — async validation jobs
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_validation_jobs (
    job_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    payload_json    JSONB           NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    result_json     JSONB,
    error_message   TEXT,
    submitted_by    VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

-- ─────────────────────────────────────────────────────────────────────
-- 7. zibo_validation_logs — validation outcome log for governance
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_validation_logs (
    log_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     UUID,
    service_name    VARCHAR(100)    NOT NULL,
    severity        VARCHAR(20)     NOT NULL,       -- ERROR, WARNING, INFORMATION
    issue_code      VARCHAR(100)    NOT NULL,
    canonical_url   VARCHAR(500),
    version         VARCHAR(50),
    details         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────────────────────────────
-- 8. zibo_event_outbox — transactional outbox for Kafka
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE zibo_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);


-- =====================================================================
-- INDEXES
-- =====================================================================

-- zibo_artifacts
CREATE INDEX idx_zibo_artifacts_canonical_version
    ON zibo_artifacts (canonical_url, version);

CREATE INDEX idx_zibo_artifacts_tenant_status
    ON zibo_artifacts (tenant_id, status);

CREATE INDEX idx_zibo_artifacts_type_tenant
    ON zibo_artifacts (fhir_type, tenant_id);

CREATE INDEX idx_zibo_artifacts_created_at
    ON zibo_artifacts (created_at DESC);

-- zibo_packs
CREATE INDEX idx_zibo_packs_tenant_status
    ON zibo_packs (tenant_id, status);

-- zibo_assignments
CREATE INDEX idx_zibo_assignments_scope
    ON zibo_assignments (scope_type, scope_id);

CREATE INDEX idx_zibo_assignments_pack
    ON zibo_assignments (pack_id);

-- zibo_mappings_index
CREATE INDEX idx_zibo_mappings_source
    ON zibo_mappings_index (source_system, source_code);

CREATE INDEX idx_zibo_mappings_target
    ON zibo_mappings_index (target_system, target_code);

CREATE INDEX idx_zibo_mappings_tenant_source
    ON zibo_mappings_index (tenant_id, source_system);

-- zibo_validation_jobs
CREATE INDEX idx_zibo_validation_jobs_tenant_status
    ON zibo_validation_jobs (tenant_id, status);

CREATE INDEX idx_zibo_validation_jobs_created_at
    ON zibo_validation_jobs (created_at DESC);

-- zibo_validation_logs
CREATE INDEX idx_zibo_validation_logs_tenant_created
    ON zibo_validation_logs (tenant_id, created_at DESC);

CREATE INDEX idx_zibo_validation_logs_severity_created
    ON zibo_validation_logs (severity, created_at DESC);

CREATE INDEX idx_zibo_validation_logs_canonical
    ON zibo_validation_logs (canonical_url);

-- zibo_event_outbox — partial index on unpublished rows for efficient polling
CREATE INDEX idx_zibo_event_outbox_unpublished
    ON zibo_event_outbox (created_at)
    WHERE published_at IS NULL;
