-- Wave-1 dual-SoR-with-mirror: raw wgv_organisation attributes carried alongside
-- the canonical fields for organizations mirrored from workforce-governance
-- (source = 'WGV_MIRROR', source_ref = wgv_organisation.id).
ALTER TABLE org_registry.org_registry_organization
    ADD COLUMN wgv_org_type_raw       VARCHAR(64),
    ADD COLUMN wgv_status_raw         VARCHAR(32),
    ADD COLUMN wgv_parent_source_ref  VARCHAR(255),
    ADD COLUMN mirrored_at            TIMESTAMPTZ;

-- Idempotency guard: one mirrored row per wgv_organisation id.
CREATE UNIQUE INDEX uq_org_registry_org_wgv_source_ref
    ON org_registry.org_registry_organization (source_ref)
    WHERE source = 'WGV_MIRROR';
