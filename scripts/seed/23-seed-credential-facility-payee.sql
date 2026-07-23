-- =============================================================================
-- Seed: facility payee credential (marketplace payment preview)
-- DB:   credential_verification
-- Why:  OF-B M3 live proof — MusheX PaymentIntentService verifies the payee
--       before an intent can go PAID (extractProviderId falls back to the
--       facility id and requires an ACTIVE credential_verification credential).
--       A fresh estate has an EMPTY credentials table, so EVERY marketplace
--       payment fails PROVIDER_CREDENTIAL_INVALID. This is the durable form of
--       the proof-run A3 adaptation (ops/seed gap, not a code bug). Idempotent.
-- =============================================================================

INSERT INTO credential_verification.credentials
  (tenant_id, subject_type, subject_id, subject_name, credential_type, title,
   issued_by, issued_at, valid_from, status, verification_token, signature_hex,
   signed_payload, created_by, created_at, updated_at)
SELECT
  '00000000-0000-4000-8000-000000000001', 'FACILITY',
  'f1000000-0000-0000-0000-000000000001', 'Preview Facility Payee',
  'FACILITY_PAYEE', 'Facility payee credential (preview seed)',
  'preview-seed', now(), now(), 'ACTIVE', 'preview-seed-token', '00', '{}',
  'preview-seed', now(), now()
WHERE NOT EXISTS (
  SELECT 1 FROM credential_verification.credentials
  WHERE tenant_id = '00000000-0000-4000-8000-000000000001'
    AND subject_id = 'f1000000-0000-0000-0000-000000000001'
    AND status = 'ACTIVE'
);
