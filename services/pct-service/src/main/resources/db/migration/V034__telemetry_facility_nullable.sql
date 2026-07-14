-- Citizen-originated teleconsult requests (virtual-care lane) carry NO facility context —
-- the citizen is not at a facility. pct_telemetry_events.facility_id NOT NULL made the
-- fine-grained telemetry insert inside createReferral abort the WHOLE clinical write with
-- a 500 (caught live by scripts/runtime-proof/virtual-care-journeys.sh J-VC-1). Telemetry
-- is an observability side-channel and must never block a clinical transaction; a null
-- facility is the honest value for citizen-lane events.
ALTER TABLE pct_telemetry_events ALTER COLUMN facility_id DROP NOT NULL;
