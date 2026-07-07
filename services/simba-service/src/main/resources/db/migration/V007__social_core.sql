-- Simba WELLNESS-SOCIAL layer (sovereign, governed). DISTINCT from the generic community-service
-- social stack (/internal/v1/social/**) which is actor-string-keyed with no person_cpid/consent/
-- care_linkage. This layer is wellness-governed: person_cpid, consent-gated sensitive categories,
-- care-linkage safety escalation. All simba-schema. Every content row carries an actor block +
-- moderation_status (VISIBLE default) + visibility (PRIVATE default).

-- ── Social profile (opt-in wellness-social identity) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_profile (
    id              BIGSERIAL PRIMARY KEY,
    social_profile_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    display_name    VARCHAR(120),
    handle          VARCHAR(64),
    bio             VARCHAR(280),
    avatar_object_id VARCHAR(128),           -- document-service object ref (never bytes)
    visibility      VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    verified_badge  VARCHAR(24),             -- PROVIDER | PROGRAMME | NONE
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_social_profile UNIQUE (tenant_id, person_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_social_profile_handle
    ON simba.simba_social_profile (tenant_id, handle);

-- ── Posts (rich wellness posts) ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_post (
    id              BIGSERIAL PRIMARY KEY,
    post_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    actor_cpid      VARCHAR(128) NOT NULL,
    actor_type      VARCHAR(24) NOT NULL DEFAULT 'CLIENT',  -- CLIENT|PROVIDER|CAREGIVER|FACILITY_PROGRAMME|MODERATOR|SYSTEM
    provider_id     VARCHAR(128),
    programme_id    UUID,
    facility_id     VARCHAR(128),
    group_id        UUID,
    community_id    UUID,
    body            TEXT NOT NULL,
    media_object_ids JSONB,                  -- array of document-service object refs
    sensitive_category VARCHAR(24),          -- MENTAL|SUBSTANCE|SRH|HIV|ADOLESCENT|SAFEGUARDING (gated)
    milestone_ref   VARCHAR(128),            -- optional sanitised milestone (never clinical values)
    visibility      VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    pinned          BOOLEAN NOT NULL DEFAULT FALSE,
    reaction_count  INT NOT NULL DEFAULT 0,
    comment_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_social_post_actor
    ON simba.simba_social_post (tenant_id, actor_cpid, id DESC);
CREATE INDEX IF NOT EXISTS idx_simba_social_post_group
    ON simba.simba_social_post (tenant_id, group_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_simba_social_post_community
    ON simba.simba_social_post (tenant_id, community_id, id DESC);

-- ── Statuses (lightweight ephemeral-style wellness updates) ────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_status (
    id              BIGSERIAL PRIMARY KEY,
    status_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    actor_cpid      VARCHAR(128) NOT NULL,
    actor_type      VARCHAR(24) NOT NULL DEFAULT 'CLIENT',
    text            VARCHAR(280) NOT NULL,
    mood            VARCHAR(24),
    visibility      VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_social_status_actor
    ON simba.simba_social_status (tenant_id, actor_cpid, id DESC);

-- ── Comments ──────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_comment (
    id              BIGSERIAL PRIMARY KEY,
    comment_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    post_id         UUID NOT NULL REFERENCES simba.simba_social_post(post_id),
    actor_cpid      VARCHAR(128) NOT NULL,
    actor_type      VARCHAR(24) NOT NULL DEFAULT 'CLIENT',
    body            TEXT NOT NULL,
    moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_social_comment_post
    ON simba.simba_social_comment (post_id, id ASC);

-- ── Reactions ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_reaction (
    id              BIGSERIAL PRIMARY KEY,
    reaction_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    subject_type    VARCHAR(16) NOT NULL,    -- POST | STATUS | COMMENT | REEL
    subject_id      UUID NOT NULL,
    actor_cpid      VARCHAR(128) NOT NULL,
    reaction        VARCHAR(16) NOT NULL,    -- ENCOURAGE|LIKE|CELEBRATE|HELPFUL|SUPPORT|THANK_YOU
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_social_reaction UNIQUE (subject_type, subject_id, actor_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_social_reaction_subject
    ON simba.simba_social_reaction (subject_type, subject_id);

-- ── Bookmarks ─────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_bookmark (
    id              BIGSERIAL PRIMARY KEY,
    bookmark_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    actor_cpid      VARCHAR(128) NOT NULL,
    subject_type    VARCHAR(16) NOT NULL,    -- POST | REEL
    subject_id      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_social_bookmark UNIQUE (actor_cpid, subject_type, subject_id)
);
CREATE INDEX IF NOT EXISTS idx_simba_social_bookmark_actor
    ON simba.simba_social_bookmark (tenant_id, actor_cpid, id DESC);

-- ── Follows ───────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_social_follow (
    id              BIGSERIAL PRIMARY KEY,
    follow_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    follower_cpid   VARCHAR(128) NOT NULL,
    followee_cpid   VARCHAR(128) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PENDING | BLOCKED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_social_follow UNIQUE (follower_cpid, followee_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_social_follow_followee
    ON simba.simba_social_follow (tenant_id, followee_cpid, status);
