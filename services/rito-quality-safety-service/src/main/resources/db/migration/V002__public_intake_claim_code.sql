-- Anonymous public intake (gateway-public-lane ADR, W4 gateway-feedback-claim):
-- a citizen submits a complaint/safety concern without an account and receives a
-- one-time claim code; only its SHA-256 hash is stored. The case reference alone is
-- deliberately NOT usable to read status (too little entropy to act as a secret).
ALTER TABLE rito.rit_case
    ADD COLUMN IF NOT EXISTS claim_code_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_rit_case_claim_code
    ON rito.rit_case (tenant_id, claim_code_hash)
    WHERE claim_code_hash IS NOT NULL;
