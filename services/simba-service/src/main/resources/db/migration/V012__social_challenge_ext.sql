-- Simba social CHALLENGE extension. Challenges are REUSED & EXTENDED (ALTER, no parallel table) so
-- the social layer can attach challenges to groups/communities/programmes and share progress to feed.

ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS group_id UUID;
ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS community_id UUID;
ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS programme_id UUID;
ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS campaign_flag BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE simba.challenges ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE';

ALTER TABLE simba.challenge_participants ADD COLUMN IF NOT EXISTS shared_to_feed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE simba.challenge_participants ADD COLUMN IF NOT EXISTS share_post_id UUID;
ALTER TABLE simba.challenge_participants ADD COLUMN IF NOT EXISTS rank INT;

CREATE INDEX IF NOT EXISTS idx_simba_challenge_group ON simba.challenges (tenant_id, group_id);
CREATE INDEX IF NOT EXISTS idx_simba_challenge_community ON simba.challenges (tenant_id, community_id);
