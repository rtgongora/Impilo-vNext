-- =====================================================================================
-- PCT V043 — biometric-verified flag on the patient journey (arrival/check-in).
--
-- When a check-in carries a biometric probe (subjectRef + probe), PCT verifies it through the
-- shared ABIS seam (zw.gov.mohcc.impilo.shared.biometric.BiometricVerificationClient). A MATCH
-- marks the arrival as biometric-verified; NO_MATCH is rejected (nothing persisted); an engine
-- UNAVAILABLE/NO_REFERENCE falls back to a non-biometric check-in so a biometric outage never
-- blocks care. Additive, NOT NULL with a safe default — existing journeys read as non-biometric.
-- =====================================================================================

ALTER TABLE pct_journeys
    ADD COLUMN IF NOT EXISTS biometric_verified BOOLEAN NOT NULL DEFAULT FALSE;
