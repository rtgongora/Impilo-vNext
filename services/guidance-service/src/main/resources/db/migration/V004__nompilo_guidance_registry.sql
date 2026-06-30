-- =============================================================================
-- guidance-service V004 — Nompilo contextual guidance registry + dismissals
--
-- Nompilo is the on-platform guide (understand where you are, what's next, why
-- something is locked, what's required to complete a safe action). Until now the
-- "what next" cards lived hardcoded in the BFF/UI (WalletOverviewService.nextActions,
-- the wallet page NextActions component). This migration makes guidance *config-driven*
-- and owned by the Nompilo SoR (guidance-service):
--
--   * guidance.guidance_item   — the catalogue: a guidance card keyed by (domain, key)
--                                applicable to a route/user-type/role/trust, with title,
--                                body, CTA route, owning service, required policy, optional
--                                Khuluma follow-up + Fundo content link, severity, audit flag.
--   * guidance.guidance_dismissal — per-person dismissed guidance (so a card a person has
--                                dismissed is not shown again). This is display state only;
--                                no SoR truth lives here.
--
-- Nompilo never owns the underlying transaction — every item routes to a sovereign owner.
-- =============================================================================

-- ── Guidance catalogue (config-driven, not hardcoded in the UI) ──────────────────────
CREATE TABLE guidance.guidance_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255) NOT NULL DEFAULT 'national-spine',
    guidance_key        VARCHAR(128) NOT NULL,        -- stable key, e.g. UPGRADE_TRUST, PROVIDER_CHECK_IN
    domain              VARCHAR(64)  NOT NULL,         -- wallet | care | payments | provider | facility | onboarding | safety | learning
    guidance_type       VARCHAR(48)  NOT NULL,         -- ORIENTATION | NEXT_BEST_ACTION | LOCKED_STATE | CHECKLIST_ITEM | STATUS_EXPLANATION | RISK_PROMPT | ESCALATION | ROUTING | RECOVERY | PROTOCOL
    -- applicability (NULL / '*' = applies to all) -----------------------------------
    applies_route       VARCHAR(255),                  -- route prefix this guidance is contextual to, e.g. /citizen/wallet
    applies_user_type   VARCHAR(32),                   -- CITIZEN | PROVIDER | GUARDIAN | FACILITY_ADMIN | SUPPORT | *
    applies_role        VARCHAR(64),                   -- optional active-role gate
    min_trust_loa       INT,                           -- minimum LoA at which this guidance is relevant (NULL = any)
    trigger_condition   VARCHAR(128),                  -- machine-readable condition key the engine evaluates (e.g. trust.not_verified)
    -- presentation ------------------------------------------------------------------
    title               VARCHAR(255) NOT NULL,
    body                TEXT         NOT NULL,
    cta_label           VARCHAR(128),
    cta_route           VARCHAR(255),                  -- route to the owning surface (no dead buttons)
    -- governance + routing ----------------------------------------------------------
    owner_service       VARCHAR(64),                   -- the SoR this guidance routes to (vito, pct, costa, ...)
    required_policy     VARCHAR(128),                  -- impilo.nompilo action this item requires to be shown
    khuluma_follow_up   BOOLEAN      NOT NULL DEFAULT FALSE, -- a Khuluma reminder/follow-up may be requested
    fundo_content_ref   VARCHAR(255),                  -- Fundo course/resource id to recommend (recommend, don't duplicate)
    severity            VARCHAR(16)  NOT NULL DEFAULT 'INFO',  -- INFO | RECOMMEND | WARN | CRITICAL
    priority            INT          NOT NULL DEFAULT 100,     -- lower = ranked higher
    audit_required      BOOLEAN      NOT NULL DEFAULT FALSE,   -- sensitive: emit an audit event when shown
    i18n_key            VARCHAR(128),                  -- localisation key
    accessibility_note  VARCHAR(255),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | RETIRED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_guidance_item_key UNIQUE (tenant_id, guidance_key)
);

CREATE INDEX idx_guidance_item_route  ON guidance.guidance_item (tenant_id, status, applies_route);
CREATE INDEX idx_guidance_item_domain ON guidance.guidance_item (tenant_id, status, domain);

-- ── Per-person dismissed guidance (display state only) ───────────────────────────────
CREATE TABLE guidance.guidance_dismissal (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(255) NOT NULL,
    actor_id      VARCHAR(255) NOT NULL,        -- the person who dismissed it
    guidance_key  VARCHAR(128) NOT NULL,
    dismissed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_guidance_dismissal UNIQUE (tenant_id, actor_id, guidance_key)
);

CREATE INDEX idx_guidance_dismissal_actor ON guidance.guidance_dismissal (tenant_id, actor_id);

-- ── Seed the catalogue with real, route-bound guidance (replaces hardcoded cards) ─────
-- Every seeded item ties to a real route / owning service. These are the canonical
-- contextual cards Nompilo surfaces; they are data, so they can be governed/localised.
INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Citizen / wallet -----------------------------------------------------------------
    ('UPGRADE_TRUST', 'wallet', 'LOCKED_STATE', '/citizen/wallet', 'CITIZEN', NULL, 'trust.not_verified',
     'Verify your identity to unlock your full health wallet',
     'Some records and actions require a higher trust level. Verifying your identity unlocks your detailed health record, document downloads, and consent controls.',
     'Verify identity', '/citizen/wallet/identity', 'identity-assurance', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 10, TRUE),
    ('COMPLETE_PROFILE', 'wallet', 'NEXT_BEST_ACTION', '/citizen/wallet', 'CITIZEN', NULL, 'profile.incomplete',
     'Complete your profile',
     'A complete profile helps providers care for you safely. Add the missing details to your Mushe Personal Health Wallet.',
     'Update profile', '/citizen/wallet/profile', 'vito', 'nompilo.guidance.context.view', FALSE, NULL, 'RECOMMEND', 40, FALSE),
    ('SETTLE_BILL', 'payments', 'NEXT_BEST_ACTION', '/citizen/wallet', 'CITIZEN', NULL, 'payments.outstanding',
     'You have outstanding bills',
     'Review and settle outstanding bills, or arrange a payment plan. We will not share bill details here — open Payments to see them securely.',
     'Review payments', '/citizen/wallet/payments', 'costa', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 30, FALSE),
    ('WALLET_WALKTHROUGH', 'onboarding', 'ORIENTATION', '/citizen/wallet', 'CITIZEN', NULL, 'first_time',
     'Welcome to your Mushe Personal Health Wallet',
     'This is your person anchor — your identity, health records, care timeline, consent, dependants, and payments in one governed place. Tap a card to explore.',
     'Take the tour', '/citizen/wallet/identity', 'guidance', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),
    ('GIVE_FEEDBACK', 'safety', 'ROUTING', '/citizen/wallet', 'CITIZEN', NULL, 'always',
     'Share feedback or report a concern',
     'Tell us about your experience or raise a quality or safety concern. Safety concerns are routed to the right team — they are never lost.',
     'Give feedback', '/my-life/feedback/new', 'rito', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 90, FALSE),
    -- Provider / work ------------------------------------------------------------------
    ('PROVIDER_CHECK_IN', 'provider', 'LOCKED_STATE', '/work', 'PROVIDER', NULL, 'provider.not_checked_in',
     'Check in to a facility workspace to start work',
     'You are signed in as a provider but not checked into a workspace. Check in to the correct facility and workspace before opening encounters.',
     'Check in', '/work/check-in', 'vashandi', 'nompilo.guidance.provider.view', FALSE, NULL, 'WARN', 10, TRUE),
    ('PROVIDER_WORKDAY', 'provider', 'CHECKLIST_ITEM', '/work', 'PROVIDER', NULL, 'provider.checked_in',
     'Your workday checklist',
     'Review your roster, open your queue, and complete encounter steps. Nompilo explains each next step — it never replaces your clinical judgement.',
     'Open my work', '/work/queue', 'pct', 'nompilo.guidance.provider.view', FALSE, NULL, 'INFO', 30, TRUE),
    ('PROVIDER_PROTOCOL', 'learning', 'PROTOCOL', '/work', 'PROVIDER', NULL, 'always',
     'Open the relevant protocol or SOP',
     'Refresh the protocol for this encounter type. Protocols and CPD are owned by Impilo Fundo — Nompilo surfaces them, it does not author them.',
     'Open Fundo', '/learning/catalog', 'fundo', 'nompilo.guidance.fundo.view', FALSE, NULL, 'RECOMMEND', 70, TRUE),
    -- Facility admin -------------------------------------------------------------------
    ('FACILITY_SETUP', 'facility', 'CHECKLIST_ITEM', '/facility', 'FACILITY_ADMIN', NULL, 'facility.setup_incomplete',
     'Finish facility-mode setup',
     'Some facility configuration is incomplete. Resolve the missing items so staff can check in and work safely.',
     'Open facility setup', '/facility/setup', 'tuso', 'nompilo.guidance.facility.view', FALSE, NULL, 'WARN', 20, TRUE);
