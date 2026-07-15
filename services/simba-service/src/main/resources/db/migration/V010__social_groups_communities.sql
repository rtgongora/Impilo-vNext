-- Simba social GROUPS + COMMUNITIES. Clubs are SUPERSEDED by simba_group (group_kind='CLUB') with a
-- clubs_compat view so legacy reads keep working until /wellness/clubs repoints.

CREATE TABLE IF NOT EXISTS simba.simba_group (
    id              BIGSERIAL PRIMARY KEY,
    group_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    group_kind      VARCHAR(24) NOT NULL DEFAULT 'GROUP',  -- GROUP | CLUB | SUPPORT | PROGRAMME_GROUP
    topic           VARCHAR(64),
    sensitive_flag  BOOLEAN NOT NULL DEFAULT FALSE,        -- gated group (e.g. mental-health support)
    community_id    UUID,
    programme_id    UUID,
    visibility      VARCHAR(24) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC | PRIVATE | INVITE_ONLY
    join_policy     VARCHAR(16) NOT NULL DEFAULT 'OPEN',   -- OPEN | REQUEST | CLOSED
    max_members     INT,
    member_count    INT NOT NULL DEFAULT 0,
    created_by      VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_group_kind
    ON simba.simba_group (tenant_id, group_kind, status);

CREATE TABLE IF NOT EXISTS simba.simba_group_membership (
    id              BIGSERIAL PRIMARY KEY,
    membership_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    group_id        UUID NOT NULL REFERENCES simba.simba_group(group_id),
    person_cpid     VARCHAR(128) NOT NULL,
    role            VARCHAR(24) NOT NULL DEFAULT 'MEMBER',  -- OWNER|ADMIN|MODERATOR|VERIFIED_PROVIDER|PROGRAMME_OFFICER|MEMBER|CAREGIVER|VIEWER
    can_post        BOOLEAN NOT NULL DEFAULT TRUE,
    can_comment     BOOLEAN NOT NULL DEFAULT TRUE,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | REQUESTED | REMOVED | LEFT
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_group_member UNIQUE (group_id, person_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_group_member_person
    ON simba.simba_group_membership (tenant_id, person_cpid, status);

CREATE TABLE IF NOT EXISTS simba.simba_community (
    id              BIGSERIAL PRIMARY KEY,
    community_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    community_type  VARCHAR(32) NOT NULL DEFAULT 'WELLNESS', -- WELLNESS | CONDITION | PROGRAMME | REGIONAL
    programme_id    UUID,
    visibility      VARCHAR(24) NOT NULL DEFAULT 'PUBLIC',
    member_count    INT NOT NULL DEFAULT 0,
    created_by      VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS simba.simba_community_membership (
    id              BIGSERIAL PRIMARY KEY,
    membership_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    community_id    UUID NOT NULL REFERENCES simba.simba_community(community_id),
    person_cpid     VARCHAR(128) NOT NULL,
    role            VARCHAR(24) NOT NULL DEFAULT 'MEMBER',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_community_member UNIQUE (community_id, person_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_community_member_person
    ON simba.simba_community_membership (tenant_id, person_cpid, status);

-- Backwards-compatible read view for legacy clubs consumers (group_kind='CLUB').
CREATE OR REPLACE VIEW simba.clubs_compat AS
SELECT g.group_id AS club_id, g.tenant_id, g.name, g.description,
       g.group_kind AS club_type, g.visibility, g.max_members, g.created_by, g.created_at
FROM simba.simba_group g
WHERE g.group_kind = 'CLUB';
