-- =============================================================================
-- MSIKA V006 — Marketplace Storefront Lane (person/citizen-facing discovery)
--
-- Adds the buyer-discoverable storefront surface ON TOP of the existing
-- offering/catalog registry (V001/V004/V005). Msika owns marketplace DISCOVERY
-- and listing lifecycle ONLY. It does NOT own:
--   * orders / carts / fulfilment   -> msika-flow-service (mf_*)
--   * billing / charges             -> Costa
--   * payments / receipts           -> MusheX
--   * stock / reservations          -> inventory-service (+ msika-flow holds)
--   * clinical orders / requests    -> PCT / OROS
-- A listing references a catalog_item_id (+ optional offering_id) and carries a
-- buyer-facing presentation, seller identity, risk/eligibility, approval and
-- publish state. Cross-service handoffs are stored as *references* only.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────
-- 1. msika_storefronts — a seller's branded presence in the marketplace
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS msika_storefronts (
    storefront_id       VARCHAR(26)     PRIMARY KEY,            -- ULID
    tenant_id           UUID,
    seller_type         VARCHAR(20)     NOT NULL,               -- PROVIDER | FACILITY | SITE | PROGRAMME | VENDOR
    seller_id           VARCHAR(128)    NOT NULL,               -- stable ref (provider/facility/site/programme/vendor)
    display_name        VARCHAR(255)    NOT NULL,
    description         TEXT,
    -- seller-identity validation outcome (validated against Varapi/Tuso/Indawo upstream) ---
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'UNVERIFIED', -- UNVERIFIED | VERIFIED | REJECTED | SUSPENDED
    verified_by         VARCHAR(128),
    verified_at         TIMESTAMPTZ,
    contact_refs        JSONB           NOT NULL DEFAULT '{}',  -- comms refs (delivered via Khuluma, not stored as PII)
    service_area        JSONB           NOT NULL DEFAULT '{}',  -- location/coverage (resolved via Tuso/Indawo/Ndila)
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_storefront_seller_type CHECK (seller_type IN ('PROVIDER','FACILITY','SITE','PROGRAMME','VENDOR')),
    CONSTRAINT chk_storefront_verification CHECK (verification_status IN ('UNVERIFIED','VERIFIED','REJECTED','SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_msika_storefronts_seller ON msika_storefronts (seller_type, seller_id);
CREATE INDEX IF NOT EXISTS idx_msika_storefronts_tenant ON msika_storefronts (tenant_id);

-- ─────────────────────────────────────────────────────────────────────
-- 2. msika_listings — buyer-discoverable listing over a catalog item / offering
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS msika_listings (
    listing_id          VARCHAR(26)     PRIMARY KEY,            -- ULID
    tenant_id           UUID,
    storefront_id       VARCHAR(26)     REFERENCES msika_storefronts(storefront_id),
    catalog_item_id     VARCHAR(26)     NOT NULL REFERENCES msika_catalog_items(item_id),
    offering_id         VARCHAR(26)     REFERENCES msika_offerings(offering_id),
    -- seller identity (mirrors storefront but denormalised for listing-level validation) ----
    seller_type         VARCHAR(20)     NOT NULL,               -- PROVIDER | FACILITY | SITE | PROGRAMME | VENDOR
    seller_id           VARCHAR(128)    NOT NULL,
    -- buyer-facing presentation -----------------------------------------------------------
    title               VARCHAR(255)    NOT NULL,
    summary             TEXT,
    -- safety / regulation -----------------------------------------------------------------
    risk_classification VARCHAR(32)     NOT NULL DEFAULT 'LOW_RISK', -- mirrors msika_catalog_items.risk_classification
    eligibility_rule    JSONB           NOT NULL DEFAULT '{}',  -- machine-readable eligibility gate
    clinical_route      VARCHAR(20),                            -- NULL | PCT | OROS  (where a regulated/clinical item routes)
    -- commerce references (NOT owned here) ------------------------------------------------
    charge_ref          VARCHAR(128),                           -- Costa chargeable/tariff ref (display only)
    price_display       VARCHAR(64),                            -- display string; truth is Costa
    fulfilment_mode     VARCHAR(30),                            -- PICKUP | DELIVERY | IN_PERSON | VIRTUAL
    fulfilment_owner    VARCHAR(30)     NOT NULL DEFAULT 'MSIKA_FLOW', -- the owning fulfilment service
    -- lifecycle ---------------------------------------------------------------------------
    status              VARCHAR(24)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | SUBMITTED | AWAITING_APPROVAL | APPROVED | REJECTED | PUBLISHED | UNPUBLISHED | SUSPENDED
    moderation_notes    TEXT,
    approved_by         VARCHAR(128),
    approved_at         TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    -- linkage refs (never duplicate the owners) -------------------------------------------
    fundo_ref           VARCHAR(128),
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    lock_version        INTEGER         NOT NULL DEFAULT 0,

    CONSTRAINT chk_listing_seller_type CHECK (seller_type IN ('PROVIDER','FACILITY','SITE','PROGRAMME','VENDOR')),
    CONSTRAINT chk_listing_status CHECK (status IN (
        'DRAFT','SUBMITTED','AWAITING_APPROVAL','APPROVED','REJECTED','PUBLISHED','UNPUBLISHED','SUSPENDED')),
    CONSTRAINT chk_listing_clinical_route CHECK (clinical_route IS NULL OR clinical_route IN ('PCT','OROS'))
);

CREATE INDEX IF NOT EXISTS idx_msika_listings_status ON msika_listings (status);
CREATE INDEX IF NOT EXISTS idx_msika_listings_item ON msika_listings (catalog_item_id);
CREATE INDEX IF NOT EXISTS idx_msika_listings_seller ON msika_listings (seller_type, seller_id);
CREATE INDEX IF NOT EXISTS idx_msika_listings_risk ON msika_listings (risk_classification);
CREATE INDEX IF NOT EXISTS idx_msika_listings_published ON msika_listings (status, published_at DESC)
    WHERE status = 'PUBLISHED';

-- ─────────────────────────────────────────────────────────────────────
-- 3. msika_listing_media — listing imagery / docs (refs only, no blobs)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS msika_listing_media (
    media_id            VARCHAR(26)     PRIMARY KEY,            -- ULID
    listing_id          VARCHAR(26)     NOT NULL REFERENCES msika_listings(listing_id) ON DELETE CASCADE,
    media_type          VARCHAR(20)     NOT NULL DEFAULT 'IMAGE', -- IMAGE | DOCUMENT | VIDEO
    media_ref           VARCHAR(512)    NOT NULL,               -- Landela/document-store ref (not a blob)
    caption             VARCHAR(255),
    sort_order          INTEGER         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_listing_media_type CHECK (media_type IN ('IMAGE','DOCUMENT','VIDEO'))
);

CREATE INDEX IF NOT EXISTS idx_msika_listing_media_listing ON msika_listing_media (listing_id, sort_order);

-- ─────────────────────────────────────────────────────────────────────
-- 4. msika_listing_favourites — buyer favourites (display preference only)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS msika_listing_favourites (
    id                  VARCHAR(26)     PRIMARY KEY,            -- ULID
    tenant_id           UUID,
    actor_id            VARCHAR(128)    NOT NULL,               -- the buyer (Health ID)
    listing_id          VARCHAR(26)     NOT NULL REFERENCES msika_listings(listing_id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_msika_favourite UNIQUE (actor_id, listing_id)
);

CREATE INDEX IF NOT EXISTS idx_msika_favourites_actor ON msika_listing_favourites (actor_id, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────
-- 5. msika_listing_audit — sensitive/regulated/cross-person/admin actions
--    (complements msika_change_log; storefront-lane specific, immutable)
-- ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS msika_listing_audit (
    id                  VARCHAR(26)     PRIMARY KEY,            -- ULID
    tenant_id           UUID,
    actor_id            VARCHAR(128)    NOT NULL,
    actor_type          VARCHAR(32),
    action              VARCHAR(48)     NOT NULL,
        -- LISTING_CREATE | LISTING_SUBMIT | LISTING_APPROVE | LISTING_REJECT | LISTING_PUBLISH
        -- | LISTING_UNPUBLISH | LISTING_SUSPEND | LISTING_REINSTATE | REGULATED_VIEW
        -- | SELLER_VALIDATE | CROSS_PERSON_VIEW | RITO_LINK | KHULUMA_REQUEST | POLICY_DENIED
    listing_id          VARCHAR(26),
    subject_ref         VARCHAR(128),                           -- buyer/subject person where relevant
    risk_classification VARCHAR(32),
    decision            VARCHAR(16),                            -- ALLOW | DENY | n/a
    detail              JSONB           NOT NULL DEFAULT '{}',
    correlation_id      VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_msika_listing_audit_listing ON msika_listing_audit (listing_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_msika_listing_audit_actor ON msika_listing_audit (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_msika_listing_audit_action ON msika_listing_audit (action, created_at DESC);
