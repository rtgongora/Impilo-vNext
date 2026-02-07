-- =====================================================================
-- Service : msika-service (MSIKA Core — Products & Services Registry)
-- Database: msika (public schema, msika_ prefix)
-- Flyway  : V001__init.sql
-- Purpose : Complete schema for national catalog of health products,
--           services, orderables, chargeables, and capability taxonomies.
-- =====================================================================

-- ─────────────────────────────────────────────────────────────────────
-- 1. msika_catalogs — versioned catalog containers
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_catalogs (
    catalog_id          VARCHAR(26)     PRIMARY KEY,  -- ULID
    tenant_id           UUID,                          -- NULL = NATIONAL
    scope               VARCHAR(10)     NOT NULL DEFAULT 'NATIONAL',  -- NATIONAL | TENANT
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | REVIEW | APPROVED | PUBLISHED
    version             VARCHAR(50)     NOT NULL DEFAULT '0.1.0',
    parent_catalog_id   VARCHAR(26)     REFERENCES msika_catalogs(catalog_id),
    checksum            VARCHAR(64),
    created_by          VARCHAR(128)    NOT NULL,
    reviewed_by         VARCHAR(128),
    approved_by         VARCHAR(128),
    published_by        VARCHAR(128),
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_catalog_scope CHECK (scope IN ('NATIONAL', 'TENANT')),
    CONSTRAINT chk_catalog_status CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'PUBLISHED'))
);

CREATE INDEX idx_msika_catalogs_tenant_scope ON msika_catalogs (tenant_id, scope);
CREATE INDEX idx_msika_catalogs_status ON msika_catalogs (status);
CREATE UNIQUE INDEX uq_msika_catalogs_name_version ON msika_catalogs (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), name, version);

-- ─────────────────────────────────────────────────────────────────────
-- 2. msika_catalog_items — unified item table for all 6 kinds
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_catalog_items (
    item_id             VARCHAR(26)     PRIMARY KEY,  -- ULID
    catalog_id          VARCHAR(26)     NOT NULL REFERENCES msika_catalogs(catalog_id),
    kind                VARCHAR(30)     NOT NULL,  -- PRODUCT | SERVICE | ORDERABLE | CHARGEABLE | CAPABILITY_FACILITY | CAPABILITY_PROVIDER
    canonical_code      VARCHAR(100)    NOT NULL,
    display_name        VARCHAR(500)    NOT NULL,
    description         TEXT,
    synonyms            TEXT[]          DEFAULT '{}',
    tags                TEXT[]          DEFAULT '{}',
    restrictions        JSONB           DEFAULT '{}',
    zibo_bindings       JSONB           DEFAULT '[]',
    metadata            JSONB           DEFAULT '{}',
    active              BOOLEAN         NOT NULL DEFAULT true,
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    lock_version        INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT chk_item_kind CHECK (kind IN (
        'PRODUCT', 'SERVICE', 'ORDERABLE', 'CHARGEABLE',
        'CAPABILITY_FACILITY', 'CAPABILITY_PROVIDER'
    ))
);

CREATE INDEX idx_msika_items_catalog_kind ON msika_catalog_items (catalog_id, kind);
CREATE UNIQUE INDEX uq_msika_items_catalog_code ON msika_catalog_items (catalog_id, canonical_code);
CREATE INDEX idx_msika_items_tags ON msika_catalog_items USING GIN (tags);
CREATE INDEX idx_msika_items_restrictions ON msika_catalog_items USING GIN (restrictions);
CREATE INDEX idx_msika_items_zibo ON msika_catalog_items USING GIN (zibo_bindings);

-- ─────────────────────────────────────────────────────────────────────
-- 3. msika_product_details — product-specific attributes
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_product_details (
    item_id             VARCHAR(26)     PRIMARY KEY REFERENCES msika_catalog_items(item_id),
    form                VARCHAR(100),          -- tablet, injection, cream, etc.
    strength            VARCHAR(100),          -- 500mg, 10ml, etc.
    route               VARCHAR(100),          -- oral, IV, topical, etc.
    uom                 VARCHAR(50),           -- unit of measure
    pack_size           INTEGER,
    barcode             VARCHAR(50),           -- GTIN-14 / GTIN-13
    batch_tracked       BOOLEAN         NOT NULL DEFAULT true,
    expiry_tracked      BOOLEAN         NOT NULL DEFAULT true,
    cold_chain          BOOLEAN         NOT NULL DEFAULT false,
    controlled          BOOLEAN         NOT NULL DEFAULT false,
    manufacturer        VARCHAR(255),
    atc_code            VARCHAR(20)            -- WHO ATC classification
);

CREATE INDEX idx_msika_product_barcode ON msika_product_details (barcode) WHERE barcode IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────
-- 4. msika_service_details — service-specific attributes
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_service_details (
    item_id             VARCHAR(26)     PRIMARY KEY REFERENCES msika_catalog_items(item_id),
    service_category    VARCHAR(100),          -- CONSULTATION, PROCEDURE, THERAPY, DIAGNOSTIC
    duration_minutes    INTEGER,
    requires_schedule   BOOLEAN         NOT NULL DEFAULT false,
    requires_referral   BOOLEAN         NOT NULL DEFAULT false,
    facility_level_min  VARCHAR(50),           -- PRIMARY, SECONDARY, TERTIARY, QUATERNARY
    specialty_required  VARCHAR(100)
);

-- ─────────────────────────────────────────────────────────────────────
-- 5. msika_orderable_details — orderable-specific attributes (OROS)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_orderable_details (
    item_id             VARCHAR(26)     PRIMARY KEY REFERENCES msika_catalog_items(item_id),
    order_type          VARCHAR(20)     NOT NULL,  -- LAB | IMAGING | PHARMACY | PROCEDURE | OTHER
    target_kind         VARCHAR(10),               -- PRODUCT | SERVICE
    target_item_id      VARCHAR(26)     REFERENCES msika_catalog_items(item_id),
    specimen_type       VARCHAR(100),
    body_site           VARCHAR(100),
    instructions_template TEXT,
    critical_result_policy JSONB        DEFAULT '{}',

    CONSTRAINT chk_order_type CHECK (order_type IN ('LAB', 'IMAGING', 'PHARMACY', 'PROCEDURE', 'OTHER')),
    CONSTRAINT chk_target_kind CHECK (target_kind IS NULL OR target_kind IN ('PRODUCT', 'SERVICE'))
);

CREATE INDEX idx_msika_orderable_type ON msika_orderable_details (order_type);

-- ─────────────────────────────────────────────────────────────────────
-- 6. msika_chargeable_details — chargeable-specific (costing/billing)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_chargeable_details (
    item_id             VARCHAR(26)     PRIMARY KEY REFERENCES msika_catalog_items(item_id),
    target_kind         VARCHAR(10),               -- PRODUCT | SERVICE
    target_item_id      VARCHAR(26)     REFERENCES msika_catalog_items(item_id),
    tariff_code         VARCHAR(100),
    pricing_refs        JSONB           DEFAULT '{}',
    cost_method_ref     VARCHAR(100),
    billable_rules      JSONB           DEFAULT '{}',

    CONSTRAINT chk_chargeable_target CHECK (target_kind IS NULL OR target_kind IN ('PRODUCT', 'SERVICE'))
);

CREATE INDEX idx_msika_chargeable_tariff ON msika_chargeable_details (tariff_code) WHERE tariff_code IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────
-- 7. msika_capability_links — capability references to services
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_capability_links (
    id                  VARCHAR(26)     PRIMARY KEY,  -- ULID
    item_id             VARCHAR(26)     NOT NULL REFERENCES msika_catalog_items(item_id),
    capability_type     VARCHAR(20)     NOT NULL,  -- FACILITY | PROVIDER
    applies_to_scope    JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_capability_type CHECK (capability_type IN ('FACILITY', 'PROVIDER'))
);

CREATE INDEX idx_msika_capability_item ON msika_capability_links (item_id);
CREATE INDEX idx_msika_capability_type ON msika_capability_links (capability_type);

-- ─────────────────────────────────────────────────────────────────────
-- 8. msika_external_sources — connector definitions for external catalogs
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_external_sources (
    source_id           VARCHAR(26)     PRIMARY KEY,  -- ULID
    tenant_id           UUID            NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    mode                VARCHAR(10)     NOT NULL,  -- REST | CSV | KAFKA
    config_encrypted    JSONB           DEFAULT '{}',
    enabled             BOOLEAN         NOT NULL DEFAULT true,
    last_run_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_source_mode CHECK (mode IN ('REST', 'CSV', 'KAFKA'))
);

CREATE INDEX idx_msika_sources_tenant ON msika_external_sources (tenant_id);

-- ─────────────────────────────────────────────────────────────────────
-- 9. msika_external_mappings — mapping queue for external code alignment
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_external_mappings (
    id                  VARCHAR(26)     PRIMARY KEY,  -- ULID
    source_id           VARCHAR(26)     NOT NULL REFERENCES msika_external_sources(source_id),
    external_code       VARCHAR(200)    NOT NULL,
    external_name       VARCHAR(500),
    internal_item_id    VARCHAR(26)     REFERENCES msika_catalog_items(item_id),
    map_type            VARCHAR(10)     NOT NULL DEFAULT 'EXACT',  -- EXACT | NARROW | BROAD
    confidence          NUMERIC(5,4)    DEFAULT 0.0000,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    reviewed_by         VARCHAR(128),
    reviewed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_map_type CHECK (map_type IN ('EXACT', 'NARROW', 'BROAD')),
    CONSTRAINT chk_mapping_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_msika_mappings_source ON msika_external_mappings (source_id);
CREATE INDEX idx_msika_mappings_status ON msika_external_mappings (status);
CREATE INDEX idx_msika_mappings_internal ON msika_external_mappings (internal_item_id) WHERE internal_item_id IS NOT NULL;

-- ─────────────────────────────────────────────────────────────────────
-- 10. msika_import_jobs — import job tracking
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_import_jobs (
    job_id              VARCHAR(26)     PRIMARY KEY,  -- ULID
    source_id           VARCHAR(26)     REFERENCES msika_external_sources(source_id),
    tenant_id           UUID            NOT NULL,
    catalog_id          VARCHAR(26)     REFERENCES msika_catalogs(catalog_id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | VALIDATING | MAPPING | REVIEW | COMPLETED | FAILED
    total_rows          INTEGER         DEFAULT 0,
    valid_rows          INTEGER         DEFAULT 0,
    duplicate_rows      INTEGER         DEFAULT 0,
    error_rows          INTEGER         DEFAULT 0,
    imported_rows       INTEGER         DEFAULT 0,
    stats               JSONB           DEFAULT '{}',
    error_report_doc_id VARCHAR(255),
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,

    CONSTRAINT chk_job_status CHECK (status IN ('PENDING', 'VALIDATING', 'MAPPING', 'REVIEW', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_msika_jobs_status ON msika_import_jobs (status);
CREATE INDEX idx_msika_jobs_tenant ON msika_import_jobs (tenant_id);

-- ─────────────────────────────────────────────────────────────────────
-- 11. msika_change_log — audit trail for all modifications
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_change_log (
    id                  VARCHAR(26)     PRIMARY KEY,  -- ULID
    tenant_id           UUID,
    actor_id            VARCHAR(128)    NOT NULL,
    action              VARCHAR(30)     NOT NULL,  -- CREATE | UPDATE | DELETE | PUBLISH | APPROVE | REJECT | ROLLBACK
    entity_type         VARCHAR(50)     NOT NULL,  -- CATALOG | ITEM | MAPPING | SOURCE
    entity_id           VARCHAR(26)     NOT NULL,
    diff                JSONB           DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_msika_changelog_entity ON msika_change_log (entity_type, entity_id);
CREATE INDEX idx_msika_changelog_actor ON msika_change_log (actor_id);
CREATE INDEX idx_msika_changelog_created ON msika_change_log (created_at DESC);

-- ─────────────────────────────────────────────────────────────────────
-- 12. msika_event_outbox — transactional outbox for Kafka
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE msika_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    aggregate_type      VARCHAR(50)     NOT NULL,
    aggregate_id        VARCHAR(128)    NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    payload             JSONB           NOT NULL,
    tenant_id           UUID,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_msika_outbox_unpublished ON msika_event_outbox (created_at)
    WHERE published_at IS NULL;
