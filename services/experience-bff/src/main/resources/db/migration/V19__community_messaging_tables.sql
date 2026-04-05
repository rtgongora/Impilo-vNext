-- V19: Community messaging and groups infrastructure.
-- Backs the CitizenMessagingController, MobileMessagingController,
-- and the community/groups feature on the Personal tab.

-- ── Conversations ────────────────────────────────────────────────
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    subject         VARCHAR(500),
    conversation_type VARCHAR(50) NOT NULL DEFAULT 'DIRECT',
    facility_id     UUID,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_tenant ON conversations (tenant_id);

-- ── Conversation Participants ────────────────────────────────────
CREATE TABLE conversation_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    participant_id  VARCHAR(255) NOT NULL,
    participant_type VARCHAR(30) NOT NULL DEFAULT 'USER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at    TIMESTAMPTZ
);

CREATE INDEX idx_conv_participants_conv ON conversation_participants (conversation_id);
CREATE INDEX idx_conv_participants_user ON conversation_participants (participant_id);

-- ── Messages ─────────────────────────────────────────────────────
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    sender_id       VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    message_type    VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conv ON messages (conversation_id, sent_at DESC);

-- ── Community Groups ─────────────────────────────────────────────
CREATE TABLE community_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    group_type      VARCHAR(50) NOT NULL DEFAULT 'SUPPORT',
    category        VARCHAR(100),
    image_url       VARCHAR(500),
    is_public       BOOLEAN NOT NULL DEFAULT TRUE,
    member_count    INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_community_groups_tenant ON community_groups (tenant_id);
CREATE INDEX idx_community_groups_type ON community_groups (group_type);

-- ── Group Members ────────────────────────────────────────────────
CREATE TABLE community_group_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES community_groups(id),
    member_id       VARCHAR(255) NOT NULL,
    role            VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, member_id)
);

CREATE INDEX idx_group_members_group ON community_group_members (group_id);
CREATE INDEX idx_group_members_member ON community_group_members (member_id);

-- ── Discussion Posts (within groups or feed) ─────────────────────
CREATE TABLE discussion_posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    group_id        UUID REFERENCES community_groups(id),
    author_id       VARCHAR(255) NOT NULL,
    title           VARCHAR(500),
    content         TEXT NOT NULL,
    post_type       VARCHAR(30) NOT NULL DEFAULT 'DISCUSSION',
    likes_count     INTEGER NOT NULL DEFAULT 0,
    comments_count  INTEGER NOT NULL DEFAULT 0,
    pinned          BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_discussion_posts_group ON discussion_posts (group_id, created_at DESC);

-- ── Discussion Comments ──────────────────────────────────────────
CREATE TABLE discussion_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES discussion_posts(id),
    author_id       VARCHAR(255) NOT NULL,
    content         TEXT NOT NULL,
    parent_id       UUID REFERENCES discussion_comments(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_discussion_comments_post ON discussion_comments (post_id, created_at);
