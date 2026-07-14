-- Campaign cancellation refunds (Lane B, crowdfunding money layer).
--
-- cancel-and-refund walks a campaign's contributions newest-first and refunds
-- each from the escrow wallet: WALLET-origin back to the donor wallet,
-- PSP/CASH_ASSISTED to a resolved-or-created donor wallet when a person ref
-- exists, otherwise to the per-tenant UNCLAIMED_DONATIONS system wallet. If
-- the escrow cannot cover a contribution (funds already released), the row is
-- held REFUND_PENDING with the shortfall honest — the escrow never goes
-- negative and nothing is invented.
ALTER TABLE mushe.bill_contributions
    ADD COLUMN refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE', -- NONE | REFUNDED | REFUND_PENDING | UNCLAIMED
    ADD COLUMN refund_txn_id UUID;

-- Request status gains CANCELLED_REFUNDING / CANCELLED_SETTLED (19/17 chars).
ALTER TABLE mushe.bill_contribution_requests
    ALTER COLUMN status TYPE VARCHAR(24);
