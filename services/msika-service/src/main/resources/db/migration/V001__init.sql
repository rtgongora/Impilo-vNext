-- =============================================================================
-- Msika — Products & Services Registry: Base Schema
-- National source of truth for all orderables, billables, and tariffs.
-- MUSheX and EHR are strict consumers; they CANNOT define their own charges.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS msika;

-- ---------------------------------------------------------------------------
-- Product Catalog: every orderable/billable item in the health system
-- ---------------------------------------------------------------------------
CREATE TABLE msika.product_catalog (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    product_code          VARCHAR(100) NOT NULL,
    name                  VARCHAR(500) NOT NULL,
    category              VARCHAR(100) NOT NULL,    -- DRUG, CONSUMABLE, LAB_TEST, IMAGING, PROCEDURE, SERVICE, DEVICE
    subcategory           VARCHAR(100),
    unit_of_measure       VARCHAR(50),
    orderable             BOOLEAN      NOT NULL DEFAULT true,
    billable              BOOLEAN      NOT NULL DEFAULT true,
    requires_prescription BOOLEAN      NOT NULL DEFAULT false,
    zibo_code             VARCHAR(100),             -- link to ZIBO terminology code
    zibo_system           VARCHAR(500),             -- terminology system URI
    description           TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, DEPRECATED, WITHDRAWN
    metadata              JSONB,                    -- extensible attributes (manufacturer, ATC code, etc.)
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_product_code UNIQUE (tenant_id, product_code)
);

CREATE INDEX idx_product_category ON msika.product_catalog (tenant_id, category, status);
CREATE INDEX idx_product_zibo     ON msika.product_catalog (zibo_code) WHERE zibo_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Tariff Engine: pricing rules for every product, by tier and payer
-- ---------------------------------------------------------------------------
-- Pricing lookup order:
--   1. Match (tenant, product, facility_tier, payer_type, effective date)
--   2. Fallback to (tenant, product, NULL tier, payer_type, effective date)
--   3. Fallback to (tenant, product, NULL tier, NULL payer, effective date)
-- The most specific match wins.
-- ---------------------------------------------------------------------------
CREATE TABLE msika.tariff_entry (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    product_id            BIGINT       NOT NULL REFERENCES msika.product_catalog(id),
    tariff_code           VARCHAR(100) NOT NULL,
    description           VARCHAR(500),
    base_price            NUMERIC(12,2) NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'USD',
    effective_from        DATE         NOT NULL,
    effective_to          DATE,                     -- NULL = open-ended
    facility_tier         VARCHAR(50),              -- NULL = applies to all tiers
    payer_type            VARCHAR(50),              -- NULL = applies to all payers (INSURANCE, SELF_PAY, GOVERNMENT)
    markup_pct            NUMERIC(5,2),             -- optional percentage markup
    rules                 JSONB,                    -- conditional pricing rules (volume discounts, etc.)
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    approved_by           VARCHAR(255),             -- actor who approved this tariff
    approved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_tariff_effective UNIQUE (tenant_id, tariff_code, effective_from, facility_tier, payer_type)
);

CREATE INDEX idx_tariff_product   ON msika.tariff_entry (tenant_id, product_id, status);
CREATE INDEX idx_tariff_effective ON msika.tariff_entry (tenant_id, effective_from, effective_to)
    WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- Service catalog: procedures, consultations, and non-product billables
-- ---------------------------------------------------------------------------
CREATE TABLE msika.service_catalog (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    service_code          VARCHAR(100) NOT NULL,
    name                  VARCHAR(500) NOT NULL,
    category              VARCHAR(100) NOT NULL,    -- CONSULTATION, PROCEDURE, THERAPY, DIAGNOSTIC
    specialty             VARCHAR(100),
    duration_minutes      INTEGER,
    requires_authorization BOOLEAN     NOT NULL DEFAULT false,
    zibo_code             VARCHAR(100),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    metadata              JSONB,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_service_code UNIQUE (tenant_id, service_code)
);

CREATE INDEX idx_service_category ON msika.service_catalog (tenant_id, category, status);

-- ---------------------------------------------------------------------------
-- Event outbox (reliable Kafka publishing)
-- ---------------------------------------------------------------------------
CREATE TABLE msika.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_msika_outbox_unpublished ON msika.event_outbox (created_at)
    WHERE published_at IS NULL;
