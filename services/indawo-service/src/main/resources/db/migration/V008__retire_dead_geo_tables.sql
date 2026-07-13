-- G4: retire indawo's dead geography schema now that ndila-service is the platform geography SoR.
-- ind_catchment_areas + ind_facility_locations have ZERO Java (no entity/repo/controller/service) —
-- they were never wired. Catchment resolution + facility↔catchment now go through ndila (reachable
-- via BFF /internal/v1/ndila/catchments/**). Drop ind_facility_locations first (it FKs both).
--
-- NOTE: ind_addresses is deliberately KEPT — it is LIVE (registry-intake flow: RegistryIntakeService,
-- IndawoPlaceModeController) and is now ndila-geocoded (G21). Fully delegating indawo addresses to
-- ndila locations is a separate, larger refactor of the intake address model (see the R2–R10
-- execution report § Deferred for operator) — not done here to avoid breaking a live journey.
DROP TABLE IF EXISTS ind_facility_locations;
DROP TABLE IF EXISTS ind_catchment_areas;
