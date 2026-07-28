-- Emergency kit / resus trolley registry (W10). Genuinely absent before this migration — inv_items
-- catalogues individual stock, inv_stores/inv_bins catalogue locations, but nothing tracks a KIT as
-- a unit (a resus trolley, an airway bag) that must be periodically verified complete against its
-- own manifest, distinct from ordinary stock replenishment.

CREATE TABLE inv_emergency_kit (
    kit_id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL,
    facility_id        UUID          NOT NULL REFERENCES inv_facilities(facility_id),
    store_id           UUID          REFERENCES inv_stores(store_id),
    name               VARCHAR(255)  NOT NULL,
    kit_type           VARCHAR(32)   NOT NULL DEFAULT 'RESUS_TROLLEY',
    -- Expected contents: [{"itemCode": "...", "itemName": "...", "expectedQty": n}, ...]. A plain
    -- JSONB manifest, not a join to inv_items — a kit's manifest is declared by clinical policy
    -- (what a resus trolley must contain), not derived from the general catalogue.
    contents_manifest  JSONB         NOT NULL DEFAULT '[]'::jsonb,
    status             VARCHAR(24)   NOT NULL DEFAULT 'IN_SERVICE',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_iek_kit_type CHECK (kit_type IN (
        'RESUS_TROLLEY', 'AIRWAY_BAG', 'PAEDS_TROLLEY', 'OBSTETRIC_EMERGENCY_KIT',
        'MASSIVE_HAEMORRHAGE_KIT', 'BURNS_KIT', 'MENTAL_HEALTH_SAFETY_KIT')),
    CONSTRAINT chk_iek_status CHECK (status IN ('IN_SERVICE', 'OUT_OF_SERVICE'))
);

CREATE INDEX idx_inv_emergency_kit_facility ON inv_emergency_kit (tenant_id, facility_id, status);

CREATE TABLE inv_emergency_kit_check (
    check_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    kit_id         UUID          NOT NULL REFERENCES inv_emergency_kit(kit_id),
    checked_by     VARCHAR(128)  NOT NULL,
    checked_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    complete       BOOLEAN       NOT NULL,
    -- [{"itemCode": "...", "expectedQty": n, "foundQty": n, "note": "..."}, ...] — required whenever
    -- complete=false. A discrepancy check with no recorded discrepancy detail is not auditable —
    -- the same value-or-reason discipline this pack applies everywhere else.
    discrepancies  JSONB,
    reseal_ref     VARCHAR(128),
    notes          TEXT,
    CONSTRAINT chk_iekc_discrepancies_required CHECK (
        complete = TRUE OR (discrepancies IS NOT NULL AND jsonb_array_length(discrepancies) > 0))
);

CREATE INDEX idx_inv_emergency_kit_check_kit ON inv_emergency_kit_check (tenant_id, kit_id, checked_at DESC);

-- The board query this pack's command view needs: every kit's LATEST check, so a stale or failed
-- check is visible without scanning the whole history per kit.
CREATE INDEX idx_inv_emergency_kit_check_latest ON inv_emergency_kit_check (kit_id, checked_at DESC);

COMMENT ON TABLE inv_emergency_kit IS
    'A tracked emergency kit/trolley as a unit (W10) — distinct from ordinary stock: periodically '
    'verified complete against its own declared manifest, not replenished item-by-item.';
COMMENT ON CONSTRAINT chk_iekc_discrepancies_required ON inv_emergency_kit_check IS
    'A discrepancy check with no recorded discrepancy detail is not auditable — value-or-reason.';
