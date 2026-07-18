-- Identity Contract §6 / C1: the patient-facing Impilo ID is encrypted at rest.
-- The column now holds an AES-256-GCM envelope (v<ver>:<iv>:<ct>), so:
--   1. widen VARCHAR(10) -> TEXT to fit the ciphertext, and
--   2. drop the plaintext value index — randomised GCM ciphertext is not
--      value-queryable; value lookups route through vito.identity_alias
--      (HMAC lookup hash + Argon2id verifier).
DROP INDEX IF EXISTS vito.idx_client_impilo_id;

ALTER TABLE vito.client
    ALTER COLUMN impilo_id TYPE text;

ALTER TABLE vito.issuance_request
    ALTER COLUMN impilo_id_issued TYPE text;

COMMENT ON COLUMN vito.client.impilo_id IS
    'Impilo ID, AES-256-GCM encrypted at rest (Identity Contract §6). Look up via the alias vault, never by value.';
COMMENT ON COLUMN vito.issuance_request.impilo_id_issued IS
    'Issued Impilo ID, AES-256-GCM encrypted at rest (Identity Contract §6).';
