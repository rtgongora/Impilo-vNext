-- Simba social fan-out feed + notifications. simba_social_timeline_event is DISTINCT from the Part-1
-- personal simba_timeline_event — UNION only at read-time in the feed's "my activity" filter.

CREATE TABLE IF NOT EXISTS simba.simba_social_timeline_event (
    id              BIGSERIAL PRIMARY KEY,
    social_event_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    audience_cpid   VARCHAR(128) NOT NULL,   -- the feed owner this row fans out to
    actor_cpid      VARCHAR(128) NOT NULL,   -- who produced the activity
    event_type      VARCHAR(32) NOT NULL,    -- POST_CREATED|STATUS_CREATED|REEL_CREATED|COMMENTED|REACTED|CHALLENGE_SHARED|GROUP_ANNOUNCEMENT
    subject_type    VARCHAR(16) NOT NULL,    -- POST | STATUS | REEL | COMMENT | GROUP | COMMUNITY | CHALLENGE
    subject_id      UUID NOT NULL,
    summary         VARCHAR(280),
    scope           VARCHAR(24) NOT NULL DEFAULT 'FOLLOWER', -- SELF|FOLLOWER|GROUP|COMMUNITY|PROGRAMME|OFFICIAL
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_social_timeline_audience
    ON simba.simba_social_timeline_event (tenant_id, audience_cpid, id DESC);

CREATE TABLE IF NOT EXISTS simba.simba_social_notification_event (
    id              BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    recipient_cpid  VARCHAR(128) NOT NULL,
    actor_cpid      VARCHAR(128),
    notification_type VARCHAR(32) NOT NULL,  -- REACTION|COMMENT|FOLLOW|MENTION|GROUP_INVITE|ANNOUNCEMENT|MODERATION
    subject_type    VARCHAR(16),
    subject_id      UUID,
    summary         VARCHAR(280),
    read_at         TIMESTAMPTZ,
    status          VARCHAR(16) NOT NULL DEFAULT 'UNREAD',  -- UNREAD | READ | DISMISSED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_social_notif_recipient
    ON simba.simba_social_notification_event (tenant_id, recipient_cpid, status, id DESC);
