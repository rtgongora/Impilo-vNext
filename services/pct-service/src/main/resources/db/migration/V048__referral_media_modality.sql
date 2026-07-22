-- TM-B5 (B5-2): media-modality downgrade ladder.
-- The runtime EFFECTIVE media modality of a teleconsult session, distinct from the requested
-- virtual_mode (the intent set at creation). The ladder steps VIDEO -> AUDIO -> ASYNC monotonically
-- on media failure / bandwidth; a provider may restore upward explicitly. NULL = "as requested"
-- (derive from virtual_mode). Kept as a plain column (not a status) because a downgrade does NOT
-- change the referral lifecycle state — the case stays ACCEPTED/IN_PROGRESS, only the media rung moves.
ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS media_modality VARCHAR(16);
