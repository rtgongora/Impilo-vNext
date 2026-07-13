-- Unified SMART card, Phase 4: patient opt-in to the card's PHR-carry function.
-- Fail-closed by design: existing cards default to FALSE (no clinical summary cached
-- on any card until the cardholder explicitly enables the PHR function). This is a
-- card-FUNCTION activation the cardholder controls, distinct from a record-sharing
-- consent (Mvumo governs sharing PHI with other parties).

ALTER TABLE mushe.cards
    ADD COLUMN phr_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN phr_consent_at TIMESTAMPTZ;

COMMENT ON COLUMN mushe.cards.phr_enabled IS
    'Cardholder opt-in to carry their health record on this card (fail-closed FALSE); gates the encounter->card PHR sync.';
COMMENT ON COLUMN mushe.cards.phr_consent_at IS
    'When the cardholder opted the card into/out of carrying their health record.';
