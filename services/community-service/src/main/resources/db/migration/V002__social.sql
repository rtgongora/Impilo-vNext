-- =============================================================================
-- Community Service — V002 social timeline schema
--
-- Adds the health-aware social layer (communities, groups, pages, posts,
-- comments, reactions, bookmarks, follows, reports, moderation cases) to the
-- existing community-service bounded context. The original CHW/outreach
-- tables in V001 are unchanged.
--
-- Tables live in the `community` schema with a `social_` prefix so it is
-- clear they belong to the social subdomain (not the CHW domain).
-- =============================================================================

-- ── Communities ─────────────────────────────────────────────────────────────
CREATE TABLE community.social_communities (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    slug                  VARCHAR(120) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    category              VARCHAR(64) NOT NULL,
    kind                  VARCHAR(16) NOT NULL DEFAULT 'public',
    cover_color           VARCHAR(32),
    icon_url              TEXT,
    cover_image_url       TEXT,
    member_count          INT NOT NULL DEFAULT 0,
    rules_json            JSONB NOT NULL DEFAULT '[]'::jsonb,
    moderator_ids_json    JSONB NOT NULL DEFAULT '[]'::jsonb,
    nompilo_assistant_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_community_slug UNIQUE (tenant_id, slug)
);
CREATE INDEX idx_social_communities_tenant ON community.social_communities (tenant_id);
CREATE INDEX idx_social_communities_category ON community.social_communities (category);

-- ── Community membership ────────────────────────────────────────────────────
CREATE TABLE community.social_community_memberships (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    community_id    UUID NOT NULL REFERENCES community.social_communities(id) ON DELETE CASCADE,
    actor_id        VARCHAR(128) NOT NULL,
    role            VARCHAR(16) NOT NULL DEFAULT 'member',
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_membership_unique UNIQUE (community_id, actor_id)
);
CREATE INDEX idx_social_membership_actor ON community.social_community_memberships (actor_id);

-- ── Groups (operational/social units, may belong to a community) ─────────────
CREATE TABLE community.social_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    community_id    UUID REFERENCES community.social_communities(id) ON DELETE SET NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    facility_id     VARCHAR(128),
    organisation_id VARCHAR(128),
    member_count    INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_social_groups_tenant ON community.social_groups (tenant_id);
CREATE INDEX idx_social_groups_community ON community.social_groups (community_id);

CREATE TABLE community.social_group_memberships (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    group_id        UUID NOT NULL REFERENCES community.social_groups(id) ON DELETE CASCADE,
    actor_id        VARCHAR(128) NOT NULL,
    role            VARCHAR(16) NOT NULL DEFAULT 'member',
    status          VARCHAR(16) NOT NULL DEFAULT 'active',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_group_membership_unique UNIQUE (group_id, actor_id)
);
CREATE INDEX idx_social_group_membership_actor ON community.social_group_memberships (actor_id);

-- ── Pages (formal entities — facility/org/programme/campaign/etc.) ──────────
CREATE TABLE community.social_pages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    kind              VARCHAR(32) NOT NULL,
    slug              VARCHAR(160) NOT NULL,
    name              VARCHAR(200) NOT NULL,
    bio               TEXT,
    icon_url          TEXT,
    cover_image_url   TEXT,
    verified          BOOLEAN NOT NULL DEFAULT FALSE,
    follower_count    INT NOT NULL DEFAULT 0,
    action_links_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    admin_ids_json    JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_pages_slug UNIQUE (tenant_id, slug)
);
CREATE INDEX idx_social_pages_tenant ON community.social_pages (tenant_id);
CREATE INDEX idx_social_pages_kind ON community.social_pages (kind);

CREATE TABLE community.social_page_followers (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    page_id      UUID NOT NULL REFERENCES community.social_pages(id) ON DELETE CASCADE,
    actor_id     VARCHAR(128) NOT NULL,
    followed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_page_follower_unique UNIQUE (page_id, actor_id)
);
CREATE INDEX idx_social_page_followers_actor ON community.social_page_followers (actor_id);

-- ── Posts ───────────────────────────────────────────────────────────────────
CREATE TABLE community.social_posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    author_actor_id     VARCHAR(128) NOT NULL,
    author_display      VARCHAR(200),
    author_role_badge   VARCHAR(64),
    author_facility     VARCHAR(200),
    kind                VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'published',
    visibility          VARCHAR(32) NOT NULL DEFAULT 'public',
    title               VARCHAR(400),
    body                TEXT NOT NULL,
    topics_json         JSONB NOT NULL DEFAULT '[]'::jsonb,
    attachments_json    JSONB NOT NULL DEFAULT '[]'::jsonb,
    community_id        UUID REFERENCES community.social_communities(id) ON DELETE SET NULL,
    group_id            UUID REFERENCES community.social_groups(id) ON DELETE SET NULL,
    page_id             UUID REFERENCES community.social_pages(id) ON DELETE SET NULL,
    facility_id         VARCHAR(128),
    pinned              BOOLEAN NOT NULL DEFAULT FALSE,
    source              VARCHAR(32) NOT NULL DEFAULT 'manual',
    nompilo_flags_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    reaction_count      INT NOT NULL DEFAULT 0,
    comment_count       INT NOT NULL DEFAULT 0,
    reaction_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    scheduled_for       TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_social_posts_tenant_published ON community.social_posts (tenant_id, published_at DESC);
CREATE INDEX idx_social_posts_status ON community.social_posts (status);
CREATE INDEX idx_social_posts_community ON community.social_posts (community_id, published_at DESC);
CREATE INDEX idx_social_posts_group ON community.social_posts (group_id, published_at DESC);
CREATE INDEX idx_social_posts_page ON community.social_posts (page_id, published_at DESC);
CREATE INDEX idx_social_posts_author ON community.social_posts (author_actor_id, created_at DESC);
CREATE INDEX idx_social_posts_visibility ON community.social_posts (visibility);

-- ── Comments ────────────────────────────────────────────────────────────────
CREATE TABLE community.social_comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    post_id             UUID NOT NULL REFERENCES community.social_posts(id) ON DELETE CASCADE,
    parent_comment_id   UUID REFERENCES community.social_comments(id) ON DELETE CASCADE,
    author_actor_id     VARCHAR(128) NOT NULL,
    author_display      VARCHAR(200),
    body                TEXT NOT NULL,
    reaction_count      INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_social_comments_post ON community.social_comments (post_id, created_at);

-- ── Reactions ───────────────────────────────────────────────────────────────
CREATE TABLE community.social_reactions (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    target_type         VARCHAR(16) NOT NULL,
    target_id           UUID NOT NULL,
    actor_id            VARCHAR(128) NOT NULL,
    reaction_type       VARCHAR(32) NOT NULL DEFAULT 'LIKE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_reaction_unique UNIQUE (target_type, target_id, actor_id)
);
CREATE INDEX idx_social_reactions_target ON community.social_reactions (target_type, target_id);

-- ── Bookmarks ───────────────────────────────────────────────────────────────
CREATE TABLE community.social_bookmarks (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    actor_id    VARCHAR(128) NOT NULL,
    post_id     UUID NOT NULL REFERENCES community.social_posts(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_social_bookmark_unique UNIQUE (actor_id, post_id)
);
CREATE INDEX idx_social_bookmarks_actor ON community.social_bookmarks (actor_id);

-- ── Reports + moderation ────────────────────────────────────────────────────
CREATE TABLE community.social_moderation_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    post_id             UUID NOT NULL REFERENCES community.social_posts(id) ON DELETE CASCADE,
    reporter_actor_id   VARCHAR(128) NOT NULL,
    reason              VARCHAR(64) NOT NULL,
    details             TEXT,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    decision            VARCHAR(32),
    rationale           TEXT,
    resolver_actor_id   VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);
CREATE INDEX idx_social_moderation_status ON community.social_moderation_cases (status, created_at DESC);

-- ── Seed data — used by demo deploys and integration tests ──────────────────
-- Tenant ID matches the local-default tenant used elsewhere (see ehr/page).
INSERT INTO community.social_communities (id, tenant_id, slug, name, description, category, kind, cover_color, member_count, rules_json, nompilo_assistant_enabled)
VALUES
  ('a1c01000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'maternal-health', 'Maternal Health Circle', 'Safe pregnancy, postnatal care, and breastfeeding support.', 'maternal_health', 'public', '#E11D48', 1240,
    '["Be respectful and supportive.", "No clinical advice without a verified clinician badge.", "Do not share other peoples private health data."]'::jsonb, TRUE),
  ('a1c01000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'diabetes-support', 'Diabetes Support Together', 'Living well with diabetes — recipes, reminders, peer support.', 'chronic_care', 'public', '#0EA5E9', 980,
    '["Keep personal data private.", "No selling of unverified products."]'::jsonb, TRUE),
  ('a1c01000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'youth-wellness', 'Youth Wellness Hub', 'Mental wellbeing, fitness, and life skills for young people.', 'youth_wellness', 'public', '#7C3AED', 1650,
    '["Be kind.", "Crisis content gets routed to support partners."]'::jsonb, TRUE),
  ('a1c01000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'chw-practice', 'CHW Community of Practice', 'Community Health Workers — peer learning, case discussions, mentorship.', 'professional', 'restricted', '#059669', 540,
    '["Verified CHW only.", "De-identify case discussions."]'::jsonb, TRUE),
  ('a1c01000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'digital-health-champions', 'Digital Health Champions', 'Champions of Impilo and digital tools in health facilities.', 'professional', 'public', '#F59E0B', 230,
    '["Constructive feedback welcomed."]'::jsonb, TRUE);

INSERT INTO community.social_groups (id, tenant_id, community_id, name, description, category)
VALUES
  ('b1c01000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'a1c01000-0000-0000-0000-000000000001', 'First-time Moms 2026', 'Cohort of expectant mothers in their first pregnancy.', 'cohort'),
  ('b1c01000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'a1c01000-0000-0000-0000-000000000004', 'Mashonaland West CHW Mentors', 'Mentorship circle for province CHW leads.', 'mentorship'),
  ('b1c01000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'a1c01000-0000-0000-0000-000000000005', 'Harare Champions Cohort', 'Implementation cohort for Harare facilities.', 'training_cohort');

INSERT INTO community.social_pages (id, tenant_id, kind, slug, name, bio, verified, follower_count, action_links_json)
VALUES
  ('c1c01000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'facility', 'parirenyatwa-hospital', 'Parirenyatwa Group of Hospitals', 'Official facility page. Service updates, campaigns, and announcements.', TRUE, 12300,
    '[{"label":"Book appointment","href":"/scheduling","kind":"action"},{"label":"View services","href":"/discover","kind":"link"}]'::jsonb),
  ('c1c01000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'campaign', 'malaria-2026', 'Malaria Prevention Campaign 2026', 'Country-wide malaria prevention drive. Bed nets, testing, treatment.', TRUE, 4500,
    '[{"label":"Find testing","href":"/discover?topic=malaria","kind":"link"}]'::jsonb),
  ('c1c01000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'programme', 'mother-and-child', 'Mother & Child Health Programme', 'Antenatal, postnatal, and child health programme updates.', TRUE, 7800,
    '[{"label":"Antenatal services","href":"/discover?topic=antenatal","kind":"link"}]'::jsonb),
  ('c1c01000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'organisation', 'mohcc', 'Ministry of Health & Child Care', 'Official Ministry channel.', TRUE, 38000,
    '[{"label":"Open ministry site","href":"https://www.mohcc.gov.zw","kind":"link"}]'::jsonb);

-- Seed posts (a few demo cards). Use static UUIDs so wiremock/tests can target them.
INSERT INTO community.social_posts (id, tenant_id, author_actor_id, author_display, author_role_badge, author_facility, kind, status, visibility, title, body, topics_json, page_id, community_id, source, reaction_count, comment_count, reaction_summary_json, published_at)
VALUES
  ('d1c01000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'system:public-health', 'MoHCC Public Health Desk', 'Public Health', 'Ministry of Health', 'announcement', 'published', 'public',
    'Malaria prevention reminders for rainy season',
    'With the rains in full swing, please sleep under insecticide-treated nets, drain stagnant water around your home, and visit your nearest facility if you have fever for more than 24 hours. Free testing is available at all primary care clinics this month.',
    '["malaria","prevention","seasonal","mohcc"]'::jsonb,
    'c1c01000-0000-0000-0000-000000000002', NULL, 'comms_hub', 412, 38,
    '{"total":412,"byType":{"LIKE":210,"SUPPORT":120,"INSIGHTFUL":82}}'::jsonb, NOW() - INTERVAL '2 hours'),
  ('d1c01000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'provider:chw-mentor', 'Nurse Tendai M.', 'Senior CHW Mentor', 'Mashonaland West', 'learning', 'published', 'public',
    'Quick refresher: how to take a respiratory rate for a child under 5',
    'Count breaths for a full minute, watch the chest rise and fall, and remember the IMCI fast-breathing thresholds. Tag a CHW you mentor — share what worked for you in the field.',
    '["chw","training","imci","under-5"]'::jsonb,
    NULL, 'a1c01000-0000-0000-0000-000000000004', 'manual', 184, 27,
    '{"total":184,"byType":{"LIKE":110,"INSIGHTFUL":74}}'::jsonb, NOW() - INTERVAL '5 hours'),
  ('d1c01000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'citizen:demo', 'Tariro K.', NULL, NULL, 'text', 'published', 'public',
    NULL,
    'Finally hit 10,000 steps every day this week thanks to the Maternal Wellness walking club. The accountability from the group really helps.',
    '["wellness","walking","community"]'::jsonb,
    NULL, 'a1c01000-0000-0000-0000-000000000001', 'manual', 98, 14,
    '{"total":98,"byType":{"LIKE":62,"CELEBRATE":36}}'::jsonb, NOW() - INTERVAL '1 day'),
  ('d1c01000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'system:facility-admin', 'Parirenyatwa Comms', 'Facility Admin', 'Parirenyatwa Hospital', 'facility_update', 'published', 'page_followers',
    'Outpatient clinic schedule for next week',
    'Diabetes clinic — Tue 08:00. Hypertension clinic — Wed 08:00. Maternal health clinic — Thu 08:00. Walk-ins welcome but appointments are recommended via the Impilo app.',
    '["outpatient","schedule","facility"]'::jsonb,
    'c1c01000-0000-0000-0000-000000000001', NULL, 'facility_page', 220, 17,
    '{"total":220,"byType":{"LIKE":180,"SUPPORT":40}}'::jsonb, NOW() - INTERVAL '6 hours');

-- Auto-bookmark the first announcement for the demo citizen so the Saved feed
-- shows something out of the box.
INSERT INTO community.social_bookmarks (tenant_id, actor_id, post_id)
VALUES ('00000000-0000-0000-0000-000000000001', 'citizen:demo', 'd1c01000-0000-0000-0000-000000000001');
