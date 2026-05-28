-- Wave 20 production-readiness demo seed (reference only)
--
-- Demo data is applied automatically via Flyway:
--   • experience-bff V4, V21, V40 (golden patient, wards, journey anchors)
--   • inpatient-service V004 (CPID-ZW-00001 admission on sovereign DB)
--
-- Manual apply is not required for compose. To verify after stack is up:
--   node scripts/production-readiness/verify-demo-journeys.mjs
--
-- Optional BFF-local care-emergency row is included in V40 (admissions table).

DO $$
BEGIN
  RAISE NOTICE 'Wave 20 demo seeds: use Flyway V40 (BFF) + V004 (inpatient-service).';
  RAISE NOTICE 'Registry: GET /internal/v1/demo-journeys';
  RAISE NOTICE 'Verify: node scripts/production-readiness/verify-demo-journeys.mjs';
END $$;
