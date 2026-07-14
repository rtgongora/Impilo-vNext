-- PSP donations ride the deposit-intent rail (Lane B, crowdfunding money layer).
--
-- A deposit intent now carries WHY the money is coming in. WALLET_TOPUP keeps
-- the original semantics (credit the wallet, done). CROWDFUNDING intents are
-- raised against a campaign's ESCROW wallet with purpose_ref = the request's
-- share token; on confirmation (PSP callback / statement match) the credit is
-- followed by recording the contribution row and advancing the raised total —
-- exactly once, never a second wallet credit.
ALTER TABLE mushe.deposit_intents
    ADD COLUMN purpose     VARCHAR(32) NOT NULL DEFAULT 'WALLET_TOPUP', -- WALLET_TOPUP | CROWDFUNDING
    ADD COLUMN purpose_ref VARCHAR(64),
    ADD COLUMN metadata    JSONB;

CREATE INDEX idx_deposit_intents_purpose_ref
    ON mushe.deposit_intents (purpose, purpose_ref)
    WHERE purpose_ref IS NOT NULL;
