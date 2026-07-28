-- =====================================================================================
-- org-registry :: V015 — source-pack load state (NCZ-W1A)
--
-- Source packs bootstrap a regulator's configuration from version-controlled files. The failure
-- mode that matters is drift: a file edited under a fixed version, so what is on disk no longer
-- matches what the Registry recorded and two people approved.
--
-- tuso's InspectionContentSeeder answers that by killing the service at startup. That is the right
-- instinct for tuso; it is the wrong trade here. org-registry sits on the regulator LOGIN path —
-- WorkContextController resolves regulatory appointments through it — so a typo in a pack file
-- would take away every regulator's ability to sign in. A configuration mistake should not become
-- an estate-wide outage.
--
-- So drift is recorded rather than fatal, and the distrust is aimed precisely:
--   * the FILE is what has changed, so the file is what stops being trusted — no further seeding
--     from that pack until a human resolves it;
--   * the RELEASE is untouched. It was approved by two people and it is what live cases are pinned
--     to; a load failure says nothing about it, so it keeps serving.
-- The failure is loud in logs and visible through a health indicator, and the pack is honest about
-- its own state rather than appearing fully loaded.
-- =====================================================================================

ALTER TABLE org_registry.regulatory_config_pack
    ADD COLUMN IF NOT EXISTS load_state VARCHAR(24) NOT NULL DEFAULT 'NEVER_LOADED',
    ADD COLUMN IF NOT EXISTS load_failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS source_manifest_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_ref VARCHAR(256),
    ADD COLUMN IF NOT EXISTS load_checked_at TIMESTAMPTZ;

ALTER TABLE org_registry.regulatory_config_pack
    DROP CONSTRAINT IF EXISTS chk_rcfg_pack_load_state;
ALTER TABLE org_registry.regulatory_config_pack
    ADD CONSTRAINT chk_rcfg_pack_load_state CHECK (load_state IN
        ('NEVER_LOADED',   -- authored through the API only; no source pack claims this pack
         'LOADED',         -- the source pack on disk agrees with the Registry
         'LOAD_FAILED'));  -- the source pack disagrees; distrust the FILE, keep serving the release

COMMENT ON COLUMN org_registry.regulatory_config_pack.load_state IS
    'Whether the version-controlled source pack agrees with the Registry. LOAD_FAILED stops further '
    'seeding from that pack; it never withdraws an activated release, which was separately approved '
    'and is what live cases are pinned to.';

-- A failed pack must be findable without scanning: it is the thing an operator is looking for.
CREATE INDEX IF NOT EXISTS idx_rcfg_pack_load_state
    ON org_registry.regulatory_config_pack (load_state)
    WHERE load_state = 'LOAD_FAILED';

-- A pack claiming to have loaded must say when, and a failed one must say why. Neither is
-- decoration: "failed" with no reason is the same as silence, and silence is what V014 closed.
ALTER TABLE org_registry.regulatory_config_pack
    DROP CONSTRAINT IF EXISTS chk_rcfg_pack_load_evidence;
ALTER TABLE org_registry.regulatory_config_pack
    ADD CONSTRAINT chk_rcfg_pack_load_evidence CHECK (
        (load_state = 'NEVER_LOADED')
        OR (load_state = 'LOADED' AND load_checked_at IS NOT NULL)
        OR (load_state = 'LOAD_FAILED' AND load_checked_at IS NOT NULL
            AND load_failure_reason IS NOT NULL AND btrim(load_failure_reason) <> '')
    );
