-- Unified SMART card (Phase 3): offline stored-value purse reconciliation.
-- A virtual/physical card signs offline transactions with its device/SE P-256 key; mushe verifies them
-- against the card's cached public key (propagated on VITO card events) and reconciles each into the
-- ledger, using a monotonic counter to reject replays/gaps. See UNIFIED_SMART_CARD_ARCHITECTURE.md.
ALTER TABLE mushe.cards
    ADD COLUMN IF NOT EXISTS vito_card_public_key TEXT,
    ADD COLUMN IF NOT EXISTS last_offline_counter BIGINT NOT NULL DEFAULT 0;
