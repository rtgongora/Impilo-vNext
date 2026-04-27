-- Link product consent request to trust-layer directive when materialised in tshepo-consent-service
ALTER TABLE mvumo.consent_request
    ADD COLUMN IF NOT EXISTS tshepo_consent_id UUID;

CREATE INDEX IF NOT EXISTS idx_mvumo_req_tshepo_consent ON mvumo.consent_request (tshepo_consent_id) WHERE tshepo_consent_id IS NOT NULL;

COMMENT ON COLUMN mvumo.consent_request.tshepo_consent_id IS 'UUID of the FHIR Consent directive in tshepo-consent-service when granted/partially granted.';
