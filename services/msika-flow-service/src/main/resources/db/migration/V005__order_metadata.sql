-- Citizen mobile requests carry notes/preferredDate as order metadata (the BFF's
-- CitizenMarketplaceController round-trips them). mf_orders never had the column,
-- so every metadata-bearing create was rejected — rig-caught contract gap.
ALTER TABLE mf_orders ADD COLUMN IF NOT EXISTS metadata JSONB;
