-- B0: card resolve seam.
-- A scanned QR (portal my-QR / wallet / emergency / digital card) carries an
-- opaque pointer = HMAC(health_id) — never the raw Health ID (Identity Contract §10).
-- To dereference a presented pointer back to the card in O(1) (instead of an
-- unscalable full-table HMAC scan), store the pointer on the card at issuance and
-- index it. The pointer is a keyed one-way HMAC, so this leaks no identity at rest.
ALTER TABLE vito.smart_card ADD COLUMN IF NOT EXISTS card_pointer VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_smart_card_pointer
    ON vito.smart_card (tenant_id, card_pointer);
