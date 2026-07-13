-- Unified SMART card (Phase 2): link a mushe money-card to the canonical VITO CardCredential.
-- VITO owns the card as an identity credential (its cardNumber is the canonical reference); mushe owns
-- the money facet. This nullable reference lets mushe react to VITO card lifecycle events
-- (impilo.vito.cards: CARD_REVOKED/SUSPENDED/REPLACED) — e.g. freeze the money card when the physical
-- SMART card is revoked. See docs/architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md.
ALTER TABLE mushe.cards ADD COLUMN IF NOT EXISTS vito_card_number VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_cards_vito_card_number
    ON mushe.cards (vito_card_number)
    WHERE vito_card_number IS NOT NULL;
