-- G4 address/geography delegation (Phase 0 seam): reference to the canonical ndila_locations
-- row materialized from this client's address. ndila-service is the geography SoR; the client
-- address remains as an offline/read cache. Populated best-effort from ndila.location.created/updated
-- events (the delegation back-hop) — nullable and non-authoritative; nothing depends on it yet.
--
-- NOTE: the canonical VITO identity table is vito.client (singular, V001) — this migration
-- originally targeted a non-existent "clients" relation, which made VITO fail to boot on any
-- fresh database (caught by the virtual-care runtime rig). The migration had never applied
-- successfully anywhere (it always errored), so fixing it in place is checksum-safe.
ALTER TABLE vito.client ADD COLUMN IF NOT EXISTS ndila_location_id UUID;

COMMENT ON COLUMN vito.client.ndila_location_id IS
    'Canonical ndila_locations id materialized from this client address (G4 delegation, best-effort). ndila is the geography SoR; the client address is a cache.';
