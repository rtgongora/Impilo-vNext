-- =============================================================================
-- Seed: Nhume courier profile (marketplace fulfilment preview)
-- DB:   nhume
-- Why:  OF-B M3 live proof — delivery assignment has a FK to
--       nhume_driver_courier_profiles; a fresh estate has no courier rows, so
--       every marketplace DELIVERY commitment stalls at assign. This is the
--       durable form of the proof-run A10 adaptation (ops/seed gap, not a
--       code bug). Idempotent.
-- =============================================================================

INSERT INTO nhume_driver_courier_profiles
  (courier_id, tenant_id, courier_code, display_name, courier_kind,
   training_status, delivery_zones_json, allowed_modes_json, current_status,
   risk_level, trust_verified, council_verified, offline_access_allowed,
   active, is_demo, created_at, updated_at)
VALUES
  ('e6000000-0000-4000-8000-0000000000e6',
   '00000000-0000-4000-8000-000000000001',
   'OFB-M3-COURIER', 'OF-B Preview Courier', 'STAFF',
   'COMPLETED', '["HARARE"]', '["MOTORBIKE"]', 'AVAILABLE',
   'LOW', true, true, false,
   true, false, now(), now())
ON CONFLICT (courier_id) DO NOTHING;
