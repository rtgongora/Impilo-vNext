-- Tamper-evident verification digest for Fundo certificate metadata (not PKI signing).

ALTER TABLE lrn_certificate
    ADD COLUMN IF NOT EXISTS verification_digest VARCHAR(128);

COMMENT ON COLUMN lrn_certificate.verification_digest IS
    'SHA-256 hex digest of canonical certificate metadata for audit verification; not a signed credential.';
