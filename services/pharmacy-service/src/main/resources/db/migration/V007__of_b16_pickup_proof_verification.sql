-- =============================================
-- Service : pharmacy-service
-- Database: pharmacy
-- Flyway  : V007__of_b16_pickup_proof_verification.sql
-- OF-B16  : pickup proof collector identity, verification grade,
--           named-recipient / SMS-reference path (§8.11.3, §12.4)
-- =============================================

-- Who physically collected, and at what verification grade on the proof ladder.
ALTER TABLE rx_pickup_proofs ADD COLUMN collector_identity VARCHAR(255);
ALTER TABLE rx_pickup_proofs ADD COLUMN verification_grade VARCHAR(40);

-- Named-recipient path: collection restricted to this named person; staff
-- verify identity at handover. The SMS reference is opaque, carries no token
-- and no clinical content; only its HMAC hash is stored (server-side
-- resolution — no token ever rides an SMS).
ALTER TABLE rx_pickup_proofs ADD COLUMN named_recipient VARCHAR(255);
ALTER TABLE rx_pickup_proofs ADD COLUMN sms_reference_hash VARCHAR(128);

CREATE INDEX idx_rx_pickup_proofs_sms_ref
    ON rx_pickup_proofs(sms_reference_hash)
    WHERE sms_reference_hash IS NOT NULL;
