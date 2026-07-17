-- =====================================================================================
-- Participation — Citizen Get-Involved & Co-Design :: V001 initial schema
-- Schema: participation.
-- Owns: citizen co-design contributions (ideas, experience feedback, testing sign-ups,
-- community-of-practice membership), a moderated public idea board with support + merge,
-- contribution lifecycle from received through released, and testing cohort enrolment.
--
-- Doctrine: this is the "something could be better" surface. It is DISTINCT from Rito
-- ("something went wrong": complaints / safety concerns). Anonymous submissions still get
-- a real reference (IDEA-YYYY-XXXXXX) and a one-time claim code (SHA-256 hashed here, never
-- stored in plaintext). The public idea board shows ONLY moderation_state = APPROVED rows.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS participation;

-- -------------------------------------------------------------------------------------
-- Core contribution aggregate — the single owning record for every citizen contribution.
-- Lifecycle (see ContributionLifecycle): RECEIVED -> UNDER_COMMUNITY_REVIEW -> BEING_EXPLORED
-- -> IN_RESEARCH -> SELECTED_FOR_CODESIGN -> PLANNED -> IN_DEVELOPMENT -> READY_FOR_TESTING
-- -> RELEASED, with NOT_FEASIBLE and MERGED as off-ramps.
-- -------------------------------------------------------------------------------------
CREATE TABLE participation.contribution (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    reference               VARCHAR(48) NOT NULL,   -- IDEA-YYYY-XXXXXX (human-readable, collision-checked)
    claim_code_hash         VARCHAR(64),            -- SHA-256 hex of one-time claim code (anonymous lane only)
    type                    VARCHAR(32) NOT NULL,   -- IDEA / EXPERIENCE / TESTING_SIGNUP / COP_MEMBERSHIP
    need_area               VARCHAR(128),           -- the health need area the idea speaks to
    title                   VARCHAR(512),
    problem_or_opportunity  TEXT,
    better_experience       TEXT,
    beneficiary             VARCHAR(255),           -- who this would help (self / caregivers / community / ...)
    willing_to_help         BOOLEAN NOT NULL DEFAULT FALSE,
    submitter_mode          VARCHAR(24) NOT NULL DEFAULT 'ANONYMOUS',  -- ANONYMOUS / AUTHENTICATED
    submitter_ref           VARCHAR(128),           -- actor ref when AUTHENTICATED; null when ANONYMOUS
    status                  VARCHAR(48) NOT NULL DEFAULT 'RECEIVED',
    merged_into_id          UUID REFERENCES participation.contribution(id),
    moderation_state        VARCHAR(24) NOT NULL DEFAULT 'PENDING',    -- PENDING / APPROVED / REJECTED (public-board gate)
    support_count           INT NOT NULL DEFAULT 0,
    roadmap_ref             VARCHAR(128),           -- opaque link to a delivery roadmap item (id reference only)
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(128),
    CONSTRAINT uq_contribution_reference UNIQUE (tenant_id, reference)
);
CREATE INDEX idx_contribution_tenant_status ON participation.contribution (tenant_id, status);
CREATE INDEX idx_contribution_tenant_type ON participation.contribution (tenant_id, type);
CREATE INDEX idx_contribution_board
    ON participation.contribution (tenant_id, moderation_state, support_count DESC);
CREATE INDEX idx_contribution_claim_code
    ON participation.contribution (tenant_id, claim_code_hash)
    WHERE claim_code_hash IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Status / lifecycle history (append-only). One row per meaningful event.
-- -------------------------------------------------------------------------------------
CREATE TABLE participation.contribution_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id     UUID NOT NULL REFERENCES participation.contribution(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    event_type          VARCHAR(48) NOT NULL,  -- RECEIVED / STATUS_CHANGED / MODERATED / MERGED / SUPPORTED / ROADMAP_LINKED / NOTE
    from_status         VARCHAR(48),
    to_status           VARCHAR(48),
    note                TEXT,
    actor_ref           VARCHAR(128),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_contribution_event_contribution
    ON participation.contribution_event (contribution_id, occurred_at);

-- -------------------------------------------------------------------------------------
-- Idea-board "support this idea". supporter_key is an opaque anonymous key OR an actor ref;
-- a supporter may support a given contribution at most once.
-- -------------------------------------------------------------------------------------
CREATE TABLE participation.contribution_support (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id     UUID NOT NULL REFERENCES participation.contribution(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    supporter_key       VARCHAR(128) NOT NULL,  -- opaque anon supporter key or authenticated actor ref
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_contribution_support UNIQUE (contribution_id, supporter_key)
);
CREATE INDEX idx_contribution_support_contribution
    ON participation.contribution_support (contribution_id);

-- -------------------------------------------------------------------------------------
-- Testing cohorts + enrolments (co-design testing / early-access participation).
-- -------------------------------------------------------------------------------------
CREATE TABLE participation.testing_cohort (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    segment             VARCHAR(48) NOT NULL,  -- CITIZEN / HEALTH_WORKER / STUDENT / ACCESSIBILITY / RURAL / URBAN / ...
    description         TEXT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_testing_cohort_active ON participation.testing_cohort (tenant_id, active);

CREATE TABLE participation.testing_enrollment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_id           UUID NOT NULL REFERENCES participation.testing_cohort(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    contributor_ref     VARCHAR(128),          -- actor ref when authenticated; null when anonymous
    contact             VARCHAR(255),          -- opaque contact (email/phone) volunteered for follow-up
    segment             VARCHAR(48),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_testing_enrollment_cohort ON participation.testing_enrollment (cohort_id);

-- -------------------------------------------------------------------------------------
-- Transactional outbox (relayed by ParticipationOutboxPublisher). Canonical Impilo shape.
-- -------------------------------------------------------------------------------------
CREATE TABLE participation.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'participation-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_participation_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_participation_outbox_unpublished
    ON participation.event_outbox (created_at) WHERE published_at IS NULL;

-- -------------------------------------------------------------------------------------
-- Seed: open testing cohorts (preview / demo). Tenant 00000000-0000-0000-0000-000000000001
-- is the canonical public-lane default tenant used by the gateway.
-- -------------------------------------------------------------------------------------
INSERT INTO participation.testing_cohort (tenant_id, name, segment, description, active) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Citizen early-access testers', 'CITIZEN',
     'Everyday citizens who help test new health-app experiences before wide release.', TRUE),
    ('00000000-0000-0000-0000-000000000001', 'Health-worker co-design panel', 'HEALTH_WORKER',
     'Frontline health workers who shape workflows and give clinical usability feedback.', TRUE),
    ('00000000-0000-0000-0000-000000000001', 'Accessibility reviewers', 'ACCESSIBILITY',
     'People who use assistive technology and help make experiences usable for everyone.', TRUE);

-- -------------------------------------------------------------------------------------
-- Seed: example board-visible ideas (moderation_state APPROVED). These demonstrate the
-- public idea board. They are anonymous, community-review stage, with a small support base.
-- -------------------------------------------------------------------------------------
INSERT INTO participation.contribution
    (tenant_id, reference, type, need_area, title, problem_or_opportunity, better_experience,
     beneficiary, willing_to_help, submitter_mode, status, moderation_state, support_count, created_by)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'IDEA-2026-0A1B2C', 'IDEA', 'Medicine availability',
     'Medicine-availability alerts',
     'People travel to a clinic or pharmacy only to find their medicine is out of stock.',
     'Let me subscribe to an alert that tells me when my medicine is back in stock nearby.',
     'People on regular medication', TRUE, 'ANONYMOUS', 'UNDER_COMMUNITY_REVIEW', 'APPROVED', 12, 'seed'),
    ('00000000-0000-0000-0000-000000000001', 'IDEA-2026-3D4E5F', 'IDEA', 'Referrals',
     'Better referral tracking',
     'After a referral I never know if the next facility received it or what happens next.',
     'Show me a simple status trail for my referral, so I know it arrived and what to do.',
     'Referred patients and caregivers', TRUE, 'ANONYMOUS', 'BEING_EXPLORED', 'APPROVED', 8, 'seed'),
    ('00000000-0000-0000-0000-000000000001', 'IDEA-2026-6A7B8C', 'IDEA', 'Health literacy',
     'Local-language health guidance',
     'Guidance is often only in English, so many people cannot fully understand their care.',
     'Offer health guidance in local languages so everyone can understand their own care.',
     'Communities with limited English', FALSE, 'ANONYMOUS', 'SELECTED_FOR_CODESIGN', 'APPROVED', 21, 'seed');

INSERT INTO participation.contribution_event (contribution_id, tenant_id, event_type, to_status, note, actor_ref)
SELECT c.id, c.tenant_id, 'RECEIVED', c.status, 'Seed example idea', 'seed'
FROM participation.contribution c
WHERE c.created_by = 'seed';
