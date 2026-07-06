-- IATG org-registry cutover — phase 2a DUAL-KEY SCAFFOLDING (additive + dormant).
--
-- Adds a NULLABLE org_registry_org_id column to each of the 5 FK-carrier tables
-- that today reference wgv_organisation.id. These columns are the phase-2c
-- forward key: once the mirror is populated and complete (soak-verified), a
-- backfill step will populate them from organization-registry ids resolved via
-- source_ref, and only then will reads/writes flip to prefer them.
--
-- Until phase-2c, the EXISTING organisation_id FK on each of these tables
-- remains AUTHORITATIVE. These columns are nullable, un-backfilled (the mirror
-- is empty), and referenced by nothing on the live path. Adding them changes no
-- current behaviour. Each is indexed so the future backfill/lookup is cheap.
--
-- NOTE: V008 is the previous highest migration; this file is pre-assigned V009 —
-- do not renumber.

ALTER TABLE wgv_hsc_employment              ADD COLUMN org_registry_org_id UUID;
ALTER TABLE wgv_facility_organisation_link  ADD COLUMN org_registry_org_id UUID;
ALTER TABLE wgv_site_organisation_link      ADD COLUMN org_registry_org_id UUID;
ALTER TABLE wgv_organisation_unit           ADD COLUMN org_registry_org_id UUID;
ALTER TABLE wgv_organisation_membership     ADD COLUMN org_registry_org_id UUID;

CREATE INDEX idx_wgv_hsc_employment_org_registry     ON wgv_hsc_employment (org_registry_org_id);
CREATE INDEX idx_wgv_fol_org_registry                ON wgv_facility_organisation_link (org_registry_org_id);
CREATE INDEX idx_wgv_sol_org_registry                ON wgv_site_organisation_link (org_registry_org_id);
CREATE INDEX idx_wgv_org_unit_org_registry           ON wgv_organisation_unit (org_registry_org_id);
CREATE INDEX idx_wgv_org_member_org_registry         ON wgv_organisation_membership (org_registry_org_id);
