-- Track whether a completed refund has already been applied to invoice balances / allocations.

ALTER TABLE costa_refunds ADD COLUMN IF NOT EXISTS allocation_reversed BOOLEAN NOT NULL DEFAULT false;
