-- Virtual and cross-facility work as a real assignment shape, not a physical
-- facility row wearing a costume (A4).
--
-- engagement_type already covers OUTREACH | TELEMED | SPECIALIST_POOL (V008)
-- and vsh_shift already anchors individual shifts to a TUSO virtual-service
-- routing seam via virtual_pool_id (V001, V006, V009). What was missing at
-- the ASSIGNMENT level (the standing posting, not the individual shift):
--
--   1. an assignment-level anchor to that same TUSO virtual-service entity,
--      so a standing "Specialist — National Telemedicine Hospital —
--      Virtual Specialist Pool" context can be resolved without waiting for
--      a shift to exist;
--   2. which organisation HOSTS/operates the virtual service (distinct from
--      organisation_id, which is the employing/appointing organisation —
--      a specialist can be employed by Facility A and host virtual sessions
--      through National Telemedicine Hospital);
--   3. cover scope: which facilities/jurisdictions an outreach team or
--      cross-facility specialist actually covers, since facility_id alone
--      cannot express "this outreach team covers five facilities".
--
-- Reuses the canonical facility/organisation record wherever a virtual
-- entity is already represented there (e.g. National Telemedicine Hospital
-- as a tuso.facility row) — virtual_pool_id is the SAME frozen TUSO
-- routing-seam target_ref vsh_shift already uses, not a parallel identifier.

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD COLUMN IF NOT EXISTS virtual_pool_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS host_organisation_id UUID,
    ADD COLUMN IF NOT EXISTS cover_scope_json JSONB;

CREATE INDEX IF NOT EXISTS idx_vsh_assignment_virtual_pool
    ON vashandi.vsh_workforce_assignment (tenant_id, virtual_pool_id) WHERE virtual_pool_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_vsh_assignment_host_org
    ON vashandi.vsh_workforce_assignment (host_organisation_id) WHERE host_organisation_id IS NOT NULL;

COMMENT ON COLUMN vashandi.vsh_workforce_assignment.virtual_pool_id IS
    'Frozen TUSO virtual-service routing-seam target_ref (same identifier '
    'space as vsh_shift.virtual_pool_id) — anchors a standing assignment to '
    'a virtual/telemedicine service without requiring a physical facility_id.';
COMMENT ON COLUMN vashandi.vsh_workforce_assignment.host_organisation_id IS
    'The organisation hosting/operating the virtual service (e.g. National '
    'Telemedicine Hospital), distinct from organisation_id (the employing/'
    'appointing organisation) — a person may be employed at Facility A and '
    'host virtual sessions through a different organisation.';
COMMENT ON COLUMN vashandi.vsh_workforce_assignment.cover_scope_json IS
    'JSON array of facility ids and/or WGV jurisdiction codes this outreach '
    'or cross-facility assignment provides cover for, e.g. '
    '["<facility-uuid>", "<facility-uuid>", "jurisdiction:district-gokwe-north"]. '
    'Null for an ordinary single-facility assignment.';
