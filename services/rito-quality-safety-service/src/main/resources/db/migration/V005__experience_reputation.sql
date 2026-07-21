-- =====================================================================================
-- Rito — Experience & Reputation :: V005 (RW1)
-- Schema: rito.  Tables prefixed rit_*.
-- Bounded capability: provider (and facility) ratings + derived reputation.
-- Doctrine: docs/doctrine/provider-reputation-doctrine.md
-- Design:   docs/architecture/rito-experience-reputation-design.md
--
-- Rito is SoR for ratings/reputation; VARAPI (provider truth) + TUSO (facility truth)
-- display governed read-only, Rito-sourced summaries only. Ratings are contextual and
-- mostly PCT-verified; the four domains are stored SEPARATELY and never blended. A rating
-- NEVER mutates licence/scope/employment/access — that firewall lives in the service layer
-- (RW3/RW7), never in a trigger here.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Contextual rating record — a Record derived from a verified Transaction (encounter).
-- Free-text narrative lives in a linked rit_case (never inline PII on the rating row).
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_provider_rating (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                     UUID NOT NULL,
    provider_public_id            VARCHAR(64) NOT NULL,   -- Varapi Provider ID (durable public id)
    facility_id                   UUID,                   -- Tuso facility
    service_point_id              UUID,                   -- Tuso service point
    encounter_ref                 VARCHAR(128),           -- PCT encounterRef (null = unverified)
    provider_role                 VARCHAR(64),            -- role during the encounter
    modality                      VARCHAR(24),            -- PHYSICAL/VIRTUAL/OUTREACH/EMERGENCY
    specialty                     VARCHAR(128),
    verified_interaction          BOOLEAN NOT NULL DEFAULT FALSE,
    verification_source           VARCHAR(24) NOT NULL DEFAULT 'NONE',  -- PCT/NONE/STAFF/CAREGIVER/REGULATORY
    respondent_class              VARCHAR(24) NOT NULL DEFAULT 'CLIENT',-- CLIENT/CAREGIVER/GUARDIAN/STAFF/ANONYMOUS
    respondent_identity_protected BOOLEAN NOT NULL DEFAULT TRUE,
    respondent_actor_ref          VARCHAR(128),           -- TSHEPO-gated; null when anonymous/protected
    reporting_period              VARCHAR(16),            -- e.g. 2026-07 / 2026-Q3
    overall_score                 NUMERIC(10,2),          -- optional composite; domains are authoritative
    narrative_case_id             UUID REFERENCES rito.rit_case(id),
    moderation_state              VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED', -- current state (history in rit_rating_moderation)
    -- Historical snapshot (service-relationship-doctrine §5): the context AT THE TIME,
    -- so the record stays correct after the provider transfers / changes status.
    provider_status_at_time       VARCHAR(48),
    assignment_ref_at_time        VARCHAR(128),
    facility_id_at_time           UUID,
    provider_role_at_time         VARCHAR(64),
    submitted_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_rating_provider   ON rito.rit_provider_rating (tenant_id, provider_public_id);
CREATE INDEX idx_rit_rating_facility   ON rito.rit_provider_rating (tenant_id, facility_id);
CREATE INDEX idx_rit_rating_encounter  ON rito.rit_provider_rating (tenant_id, encounter_ref);
CREATE INDEX idx_rit_rating_moderation ON rito.rit_provider_rating (tenant_id, moderation_state);
-- One verified rating per (encounter, provider): the anti-manipulation invariant.
CREATE UNIQUE INDEX uq_rit_rating_verified_encounter
    ON rito.rit_provider_rating (tenant_id, encounter_ref, provider_public_id)
    WHERE verified_interaction AND encounter_ref IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Per-domain scores — the four domains stored SEPARATELY, never blended.
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_rating_domain_score (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating_id     UUID NOT NULL REFERENCES rito.rit_provider_rating(id) ON DELETE CASCADE,
    tenant_id     UUID NOT NULL,
    domain        VARCHAR(32) NOT NULL,  -- CLIENT_EXPERIENCE/ACCESS_PROCESS/PROFESSIONAL_QUALITY/SAFETY_ACCOUNTABILITY
    measure       VARCHAR(64) NOT NULL,  -- communication/respect_dignity/waiting_experience/explanation_of_care/...
    score         NUMERIC(10,2) NOT NULL,
    scale         VARCHAR(16) NOT NULL DEFAULT '1-5',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_domain_score UNIQUE (rating_id, domain, measure)
);
CREATE INDEX idx_rit_domain_score_rating ON rito.rit_rating_domain_score (rating_id);
CREATE INDEX idx_rit_domain_score_domain ON rito.rit_rating_domain_score (tenant_id, domain);

-- -------------------------------------------------------------------------------------
-- Derived reputation aggregate — NEVER a source of truth; recomputed by the aggregator.
-- Public reads use verified_* only. NULL facility/service_point = "all" rollup.
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_provider_reputation_summary (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    provider_public_id    VARCHAR(64) NOT NULL,
    facility_id           UUID,               -- NULL = across all facilities
    service_point_id      UUID,               -- NULL = across all service points
    domain                VARCHAR(32) NOT NULL,
    reporting_period      VARCHAR(16) NOT NULL,
    rating_count          INT NOT NULL DEFAULT 0,
    verified_count        INT NOT NULL DEFAULT 0,
    mean_score            NUMERIC(10,2),
    verified_mean_score   NUMERIC(10,2),
    distribution          JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_recomputed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Nullable facility/service_point folded to the nil UUID so the aggregate key is unique
-- even for the "all" rollups (Postgres treats NULLs as distinct in a plain UNIQUE).
CREATE UNIQUE INDEX uq_rit_reputation_summary ON rito.rit_provider_reputation_summary (
    tenant_id, provider_public_id,
    COALESCE(facility_id, '00000000-0000-0000-0000-000000000000'::uuid),
    COALESCE(service_point_id, '00000000-0000-0000-0000-000000000000'::uuid),
    domain, reporting_period
);
CREATE INDEX idx_rit_reputation_provider ON rito.rit_provider_reputation_summary (tenant_id, provider_public_id);

-- -------------------------------------------------------------------------------------
-- Moderation / dispute record — SUBMITTED→UNDER_REVIEW→PUBLISHED|WITHHELD|DISPUTED→RESOLVED.
-- Manipulation detection (coordinated / retaliation / review-bombing) gates publication.
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_rating_moderation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating_id           UUID NOT NULL REFERENCES rito.rit_provider_rating(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    state               VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    manipulation_flags  JSONB NOT NULL DEFAULT '[]'::jsonb,  -- COORDINATED/RETALIATION/REVIEW_BOMBING/DUPLICATE
    reviewer_actor_id   VARCHAR(128),
    decision            VARCHAR(32),           -- PUBLISH/WITHHOLD/UPHOLD_DISPUTE/REJECT_DISPUTE
    decision_reason     TEXT,
    dispute_case_id     UUID REFERENCES rito.rit_case(id),
    appeal_reference    VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_moderation_rating ON rito.rit_rating_moderation (rating_id);
CREATE INDEX idx_rit_moderation_state  ON rito.rit_rating_moderation (tenant_id, state);

-- -------------------------------------------------------------------------------------
-- Provider response to feedback — part of the record.
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_provider_response (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rating_id           UUID NOT NULL REFERENCES rito.rit_provider_rating(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    provider_public_id  VARCHAR(64) NOT NULL,
    response_text       TEXT NOT NULL,
    responded_by        VARCHAR(128),
    status              VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED/PUBLISHED/WITHHELD
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_provider_response_rating ON rito.rit_provider_response (rating_id);
