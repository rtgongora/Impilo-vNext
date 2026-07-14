-- G4 address/geography delegation (Phase 0 seam): reference to the canonical ndila_locations
-- row materialized from this client's address. ndila-service is the geography SoR; clients.address
-- remains as an offline/read cache. Populated best-effort from ndila.location.created/updated events
-- (the delegation back-hop) — nullable and non-authoritative; nothing depends on it yet.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS ndila_location_id UUID;

COMMENT ON COLUMN clients.ndila_location_id IS
    'Canonical ndila_locations id materialized from this client address (G4 delegation, best-effort). ndila is the geography SoR; clients.address is a cache.';
