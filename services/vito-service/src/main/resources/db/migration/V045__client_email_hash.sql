-- Identity Journey Doctrine §2 / Wave F: private server-side contact matching.
-- email_hash mirrors phone_hash as a deterministic HMAC lookup key (never
-- exposed; resolve-contact returns only healthId or a uniform miss).
ALTER TABLE vito.client ADD COLUMN IF NOT EXISTS email_hash VARCHAR(128);
CREATE INDEX IF NOT EXISTS idx_client_email_hash ON vito.client (tenant_id, email_hash) WHERE email_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_client_phone_hash ON vito.client (tenant_id, phone_hash) WHERE phone_hash IS NOT NULL;
COMMENT ON COLUMN vito.client.email_hash IS 'HMAC lookup key for private contact matching (Identity Journey Doctrine §2)';
