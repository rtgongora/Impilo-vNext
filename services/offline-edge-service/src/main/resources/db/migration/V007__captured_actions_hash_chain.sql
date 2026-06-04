-- Wave 22 follow-up: hash chain + idempotency on captured actions

ALTER TABLE ofe_captured_actions
    ADD COLUMN IF NOT EXISTS hash_chain_prev VARCHAR(128);

ALTER TABLE ofe_captured_actions
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
