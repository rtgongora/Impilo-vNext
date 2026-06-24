-- G032: a SMART card is REQUESTED before its secure-element keypair exists, so the
-- public key is legitimately absent at request time. Previously the column was NOT NULL
-- and the service fabricated a "DEV-PLACEHOLDER-<uuid>" value to satisfy it — a fake key
-- that any downstream verifier would trust. Drop the NOT NULL so the key can be absent
-- until it is provisioned at print; activation now fails closed without a real key.
ALTER TABLE vito.smart_card ALTER COLUMN public_key DROP NOT NULL;
