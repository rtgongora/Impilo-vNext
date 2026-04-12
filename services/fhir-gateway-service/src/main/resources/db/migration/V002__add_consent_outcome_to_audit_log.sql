-- ============================================================
-- FHIR Gateway Service
-- V002: Add consent_outcome column to fhir_audit_log
--
-- Health OS doctrine: "Privacy by Architecture" — every FHIR
-- access decision records the consent evaluation outcome.
-- ============================================================

ALTER TABLE fhir_gateway.fhir_audit_log
    ADD COLUMN consent_outcome VARCHAR(32);

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.consent_outcome IS
    'Consent evaluation outcome: PERMIT, DENY, NOT_APPLICABLE, or BREAK_GLASS_BYPASS';

CREATE INDEX idx_fhir_audit_consent_outcome
    ON fhir_gateway.fhir_audit_log (consent_outcome);
