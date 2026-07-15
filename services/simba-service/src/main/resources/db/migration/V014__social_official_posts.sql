-- Simba social OFFICIAL-post flag. Distinct from `pinned`: `official` marks a post authored under an
-- official group/community role (OWNER/ADMIN/MODERATOR/VERIFIED_PROVIDER/PROGRAMME_OFFICER) — e.g. a
-- group announcement — so the feed/UI can badge it and the programme-engagement dashboard can count
-- official engagement without leaking per-person social content.

ALTER TABLE simba.simba_social_post ADD COLUMN IF NOT EXISTS official BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_simba_social_post_official
    ON simba.simba_social_post (tenant_id, official, id DESC);
