-- Unified SMART card (Phase 3): a card exists in VIRTUAL form first (a wallet pass on the phone,
-- keyed by the device keystore) — not only as a printed physical card. card_form distinguishes them;
-- the identity/financial/PHR crypto is identical either way (the SE keypair is just software-backed on
-- a virtual card). See docs/architecture/UNIFIED_SMART_CARD_ARCHITECTURE.md.
ALTER TABLE vito.smart_card
    ADD COLUMN IF NOT EXISTS card_form VARCHAR(16) NOT NULL DEFAULT 'PHYSICAL';
