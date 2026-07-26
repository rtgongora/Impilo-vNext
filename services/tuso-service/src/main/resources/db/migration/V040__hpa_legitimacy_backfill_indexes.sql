-- =====================================================================================
-- tuso :: V040 — Supporting indexes for the HPA legitimacy backfill (HAR W1)
-- The backfill selects HPA-created facilities that still carry NO source-legitimacy verdict, and
-- reads back the regulator's asserted regulatory_status per facility. Index only — the verdict rows
-- themselves are written through FacilitySourceLegitimacyService.stampFromImport so that every one
-- publishes its outbox event and audit trail; a raw INSERT here would create legitimacy the rest of
-- the platform never hears about.
-- =====================================================================================

-- Candidate selection: HPA-listed facilities awaiting a verdict.
CREATE INDEX IF NOT EXISTS idx_facility_regulatory_status_tenant
    ON tuso.facility (tenant_id, regulatory_status);

-- The NOT EXISTS anti-join against recorded verdicts (facility_id holds the canonical facility UUID).
CREATE INDEX IF NOT EXISTS idx_fsl_facility
    ON tuso.facility_source_legitimacy (facility_id);

-- Reading back the regulator's asserted status for a facility.
CREATE INDEX IF NOT EXISTS idx_ffa_facility_field
    ON tuso.facility_field_assertion (facility_id, field_path);
