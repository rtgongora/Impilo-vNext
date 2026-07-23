-- =============================================
-- Service : pharmacy-service
-- Database: pharmacy
-- Flyway  : V008__widen_pickup_proof_method.sql
-- M3 BUG-9: rx_pickup_proofs.method was VARCHAR(10) (V001) but OF-B16 added
--           PickupMethod.SMS_REFERENCE (13 chars) without widening the column,
--           so every SMS-reference proof insert failed with a
--           DataIntegrityViolation — the named-recipient/SMS path (§8.11.3)
--           had never run on this schema. Widened with headroom for the enum.
-- =============================================

ALTER TABLE rx_pickup_proofs ALTER COLUMN method TYPE VARCHAR(32);
