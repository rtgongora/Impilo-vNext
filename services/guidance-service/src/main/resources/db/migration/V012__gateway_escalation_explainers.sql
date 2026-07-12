-- =============================================================================
-- guidance-service V012 — Gateway trust-escalation explainers (public lane)
--
-- Health Services Gateway doctrine §9: every meaningful trust escalation is
-- mediated by Nompilo, never announced as a bare "authentication required".
-- Each explainer is keyed by a stable step id (an escalation point in a gateway
-- journey) and carries the four doctrinal parts in plain citizen language:
--
--   why              — what is being protected (never security internals)
--   level_label      — what level of verification is required (no more than needed)
--   what_next        — progress is saved; the person returns to the exact step
--   how_to_get_help  — real assistance routes, not a FAQ pointer
--
-- This content is served through the service-side PublicGuidanceController
-- (ADR gateway-public-lane-security: allow-listed DTOs, NO personalization,
-- NO PII, no internal service names in any seeded copy) and proxied by the
-- experience-bff under /internal/v1/public/gateway/guidance/**.
--
-- W1 rung transitions seeded (progressive trust ladder §4):
--   R0→R1 browse→save          : verify-contact
--   R1→R2 save→personal        : signin-to-book (get-care flavour),
--                                signin-to-personal (generic)
--   R2→R3 personal→sensitive   : step-up-results
-- =============================================================================

CREATE TABLE guidance.gateway_escalation_explainer (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(255) NOT NULL DEFAULT 'national-spine',
    step_key                VARCHAR(128) NOT NULL,   -- stable escalation-point id, e.g. signin-to-book
    pillar                  VARCHAR(64),             -- owning intent pillar (doctrine §2), e.g. get-care; NULL = cross-pillar
    rung_from               VARCHAR(8)  NOT NULL,    -- trust rung before the step (R0..R5)
    rung_to                 VARCHAR(8)  NOT NULL,    -- trust rung the step establishes
    title                   VARCHAR(255) NOT NULL,
    why                     TEXT         NOT NULL,   -- §9(1) why the escalation is needed
    level_label             VARCHAR(512) NOT NULL,   -- §9(2) what level of verification is required
    what_next               TEXT         NOT NULL,   -- §9(3) what happens next (progress saved)
    how_to_get_help         TEXT         NOT NULL,   -- §9(4) real help routes
    continue_without_label  VARCHAR(128),            -- label for the "Continue without signing in" option, where the activity permits it
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RETIRED
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gateway_explainer_step UNIQUE (tenant_id, step_key)
);

CREATE INDEX idx_gateway_explainer_status ON guidance.gateway_escalation_explainer (tenant_id, status);

-- ── W1 seed content (plain language; no internal service names; no security internals) ──
INSERT INTO guidance.gateway_escalation_explainer
    (step_key, pillar, rung_from, rung_to, title, why, level_label, what_next, how_to_get_help, continue_without_label)
VALUES
    ('verify-contact', NULL, 'R0', 'R1',
     'Confirm a way to reach you',
     'You asked us to save your progress or keep you updated. Confirming a phone number or email keeps what you save private to you, and lets us reach you with updates or a call back if you need help.',
     'Just a working phone number or email address — no Health ID, account, or documents are needed for this step.',
     'We will send you a one-time code. Enter it and you will come straight back to this exact step — nothing you have entered or chosen is lost.',
     'Did not get the code? You can ask for it to be resent, switch between phone and email, or request a call back. If you are stuck, the helpdesk can assist without you having to start over.',
     'Continue without saving'),

    ('signin-to-book', 'get-care', 'R1', 'R2',
     'Sign in to book your visit',
     'Appointments become part of your personal health record, so the booking must be made in your name. Signing in protects your visit details and makes sure only you (or someone you authorise) can see or change them.',
     'Sign in with your account or Health ID. If you do not have one yet, creating an account takes a few minutes — and everything you have chosen so far will still be here.',
     'Your booking details are saved. As soon as you sign in you will return to this exact step to confirm your visit — you will not have to search again or re-enter anything.',
     'Forgotten your password or lost access to your Health ID? Use the recovery options on the sign-in page, or ask for assisted access — the helpdesk can verify you and give you a reference number so you never have to start over.',
     'Continue without signing in'),

    ('signin-to-personal', NULL, 'R1', 'R2',
     'Sign in to continue',
     'The next step works with information that belongs to you personally. Signing in makes sure it is saved to the right person and that nobody else can see or change it.',
     'Sign in with your account or Health ID — nothing more is needed for this step. If you do not have an account yet, creating one takes a few minutes.',
     'Your progress is saved. After you sign in you will return to this exact step with everything you have entered still in place.',
     'If you cannot sign in — forgotten password, lost phone, or a Health ID you cannot access — use the recovery options on the sign-in page or contact the helpdesk for assisted access with a reference number.',
     'Continue without signing in'),

    ('step-up-results', NULL, 'R2', 'R3',
     'One quick extra check',
     'Test results and health records are among the most private information we hold for you. A quick extra confirmation protects them even if someone else got hold of your password or device.',
     'A one-time confirmation — such as a code sent to you — on top of your normal sign-in. It is asked only for sensitive steps like this one, not for everything you do.',
     'Your place is saved. Complete the confirmation and you will land back exactly where you were, with the information you asked for.',
     'If the code does not arrive you can resend it or use another confirmation method. If none of them work for you, the helpdesk can arrange an alternative check — you will not lose access to your own records.',
     NULL);
