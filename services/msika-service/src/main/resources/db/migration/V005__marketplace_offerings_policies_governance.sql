-- =============================================================================
-- MSIKA V005 — Marketplace Offerings + Fulfillment Policies + Governance Records
-- Adds first-class offering and fulfillment policy models without collapsing
-- transaction orchestration (Msika Flow) into catalog governance (Msika Core).
-- =============================================================================

-- Offerings: who offers what, where, and under which modes/capabilities
CREATE TABLE IF NOT EXISTS msika_offerings (
    offering_id         VARCHAR(26)     PRIMARY KEY,  -- ULID
    tenant_id           UUID,                          -- NULL = NATIONAL defaults
    catalog_item_id     VARCHAR(26)     NOT NULL REFERENCES msika_catalog_items(item_id),
    offering_type       VARCHAR(30)     NOT NULL,      -- VENDOR | FACILITY | PROVIDER | PARTNER
    provider_type       VARCHAR(30),                  -- VENDOR | FACILITY | PROVIDER | LOGISTICS
    provider_ref        VARCHAR(255)    NOT NULL,      -- stable ref (uuid/string)
    facility_ref        UUID,
    vendor_ref          UUID,
    active              BOOLEAN         NOT NULL DEFAULT true,
    availability_state  VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|PAUSED|SUSPENDED
    inventory_mode      VARCHAR(30),                  -- STOCKED|ON_DEMAND|THIRD_PARTY
    booking_mode        VARCHAR(30),                  -- SLOT|WALKIN|VIRTUAL
    pickup_modes_supported JSONB        NOT NULL DEFAULT '[]',
    delivery_modes_supported JSONB      NOT NULL DEFAULT '[]',
    lead_time_minutes   INTEGER,
    geographic_coverage JSONB           DEFAULT '{}',
    metadata            JSONB           DEFAULT '{}',
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_msika_offerings_item ON msika_offerings (catalog_item_id, active);
CREATE INDEX IF NOT EXISTS idx_msika_offerings_provider ON msika_offerings (provider_ref, active);

-- Fulfillment policies: constraints/requirements per item or offering
CREATE TABLE IF NOT EXISTS msika_fulfillment_policies (
    policy_id           VARCHAR(26)     PRIMARY KEY, -- ULID
    tenant_id           UUID,
    catalog_item_id     VARCHAR(26)     REFERENCES msika_catalog_items(item_id),
    offering_id         VARCHAR(26)     REFERENCES msika_offerings(offering_id),
    allowed_fulfillment_modes JSONB      NOT NULL DEFAULT '[]',
    identity_requirements JSONB          NOT NULL DEFAULT '{}',
    delivery_constraints JSONB           NOT NULL DEFAULT '{}',
    substitution_policy JSONB            NOT NULL DEFAULT '{}',
    custody_requirements JSONB           NOT NULL DEFAULT '{}',
    proof_requirements  JSONB            NOT NULL DEFAULT '{}',
    restrictions_summary TEXT,
    active              BOOLEAN         NOT NULL DEFAULT true,
    metadata            JSONB           DEFAULT '{}',
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_msika_policies_item ON msika_fulfillment_policies (catalog_item_id, active);
CREATE INDEX IF NOT EXISTS idx_msika_policies_offering ON msika_fulfillment_policies (offering_id, active);

-- Governance records: explicit review/decision records for catalogs/items/offerings/policies
CREATE TABLE IF NOT EXISTS msika_governance_records (
    record_id           VARCHAR(26)     PRIMARY KEY, -- ULID
    tenant_id           UUID,
    target_type         VARCHAR(50)     NOT NULL,    -- CATALOG|ITEM|OFFERING|POLICY
    target_id           VARCHAR(26)     NOT NULL,
    review_type         VARCHAR(50)     NOT NULL,    -- CURATION|REGULATORY|PRICING|FULFILLMENT
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING|APPROVED|REJECTED
    reviewer_ref        VARCHAR(128),
    decision            VARCHAR(20),
    notes               TEXT,
    evidence_refs       JSONB           DEFAULT '[]',
    reviewed_at         TIMESTAMPTZ,
    created_by          VARCHAR(128)    NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_msika_gov_target ON msika_governance_records (target_type, target_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_msika_gov_status ON msika_governance_records (status, created_at DESC);

