-- Crowdfunding money layer (Lane B, part 1): campaign-backed contribution requests.
--
-- A crowdfunding campaign (case layer: Daidzai) is backed 1:1 by a
-- bill_contribution_request with origin='CAMPAIGN'. For campaign requests the
-- beneficiary_wallet_id points at a dedicated ESCROW wallet
-- (ownerType='CROWDFUND_ESCROW', ownerRef=campaign_ref) and
-- beneficiary_target_wallet_id at the real beneficiary; funds only reach the
-- beneficiary through an explicit governed release. Plain 'BILL' requests keep
-- the original direct-credit behaviour.
ALTER TABLE mushe.bill_contribution_requests
    ADD COLUMN origin                       VARCHAR(24) NOT NULL DEFAULT 'BILL', -- BILL | CAMPAIGN
    ADD COLUMN campaign_ref                 UUID,
    ADD COLUMN beneficiary_target_wallet_id UUID;

-- Where each contribution's money actually came from:
--   WALLET        — atomic donor-wallet debit + (escrow) credit
--   PSP           — external PSP donation settled through a deposit intent
--   CASH_ASSISTED — recorded bare credit (cash in hand at a desk), flagged
ALTER TABLE mushe.bill_contributions
    ADD COLUMN contributor_wallet_id UUID,
    ADD COLUMN origin                VARCHAR(24) NOT NULL DEFAULT 'WALLET';

CREATE INDEX idx_bill_contrib_requests_campaign
    ON mushe.bill_contribution_requests (campaign_ref)
    WHERE campaign_ref IS NOT NULL;
