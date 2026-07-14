-- =====================================================================================
-- Daidzai V003 — Assistance requests (crowdfunding cases).
-- PO doctrine (2026-07-14): crowdfunding is a form of ASKING FOR HELP, so the case
-- anchors to Daidzai — the help-request command plane. Daidzai owns the case aggregate
-- and its timeline, and references every other truth by id only:
--   money    -> mushe bill_contribution_request (escrow, contributions, refunds)
--   evidence -> credential-verification signed MEDICAL_CASE_ATTESTATION (public token)
--   consent  -> tshepo consent / vito patient-share refs
-- No PII beyond what the requester volunteers for presentation; the subject can be
-- presented ANONYMOUSLY (mirrors dai_emergency_request.subject_identity_mode).
-- =====================================================================================

CREATE TABLE daidzai.dai_assistance_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    assistance_reference    VARCHAR(48) NOT NULL,
    -- Who is asking (requester may differ from the beneficiary subject)
    requester_type          VARCHAR(32) NOT NULL,   -- CITIZEN/CAREGIVER/PROVIDER/FACILITY
    requester_actor_id      VARCHAR(128),           -- actor id (audit); required on this lane
    -- Subject/beneficiary presentation (identity by ref; ANONYMOUS hides it publicly)
    subject_identity_mode   VARCHAR(24) NOT NULL DEFAULT 'ANONYMOUS', -- KNOWN/ANONYMOUS
    subject_health_id       VARCHAR(128),           -- Vito Health ID when KNOWN
    -- The ask
    category                VARCHAR(48) NOT NULL DEFAULT 'MEDICAL_COSTS', -- MEDICAL_COSTS/...
    title                   VARCHAR(255) NOT NULL,
    story                   TEXT,
    target_amount           NUMERIC(14,2) NOT NULL,
    currency                VARCHAR(8) NOT NULL DEFAULT 'USD',
    ends_at                 TIMESTAMPTZ,
    -- Verification lifecycle: only verified-ACTIVE cases are publicly listed.
    verification_status     VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT -> PENDING_VERIFICATION -> ACTIVE | REJECTED | REVOKED
        -- ACTIVE -> FULFILLED | CANCELLED
    -- Owner-routed seam references (Daidzai holds the link, the owner holds the record)
    contribution_request_id UUID,                   -- mushe bill_contribution_request id
    share_token             VARCHAR(64),            -- mushe share token (public donate link)
    consent_ref             VARCHAR(128),           -- tshepo consent / patient-share ref
    attestation_credential_id VARCHAR(128),         -- credential-verification credential id
    attestation_public_token  VARCHAR(128),         -- public verify token (donor-checkable)
    verifying_org_ref       VARCHAR(128),           -- attesting facility/org ref
    verified_at             TIMESTAMPTZ,
    -- Money projection (event-driven from mushe contribution events; never written by API)
    raised_amount           NUMERIC(14,2) NOT NULL DEFAULT 0,
    donor_count             INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dai_assistance_reference UNIQUE (tenant_id, assistance_reference),
    CONSTRAINT chk_dai_assistance_verification CHECK (verification_status IN
        ('DRAFT','PENDING_VERIFICATION','ACTIVE','REJECTED','REVOKED','FULFILLED','CANCELLED')),
    CONSTRAINT chk_dai_assistance_target CHECK (target_amount > 0)
);
CREATE INDEX idx_dai_assistance_tenant_status
    ON daidzai.dai_assistance_request (tenant_id, verification_status);
CREATE INDEX idx_dai_assistance_requester
    ON daidzai.dai_assistance_request (tenant_id, requester_actor_id);
CREATE UNIQUE INDEX uq_dai_assistance_contribution
    ON daidzai.dai_assistance_request (contribution_request_id)
    WHERE contribution_request_id IS NOT NULL;

-- Append-only case timeline (mirrors dai_mission_event): every verification/lifecycle
-- transition is an honest, audited observation.
CREATE TABLE daidzai.dai_assistance_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    assistance_id       UUID NOT NULL REFERENCES daidzai.dai_assistance_request (id) ON DELETE CASCADE,
    event_type          VARCHAR(32) NOT NULL,   -- CREATED/VERIFICATION_REQUESTED/ATTESTED/REJECTED/REVOKED/CONTRIBUTION/RELEASED/FULFILLED/CANCELLED
    from_status         VARCHAR(24),
    to_status           VARCHAR(24),
    note                VARCHAR(1024),
    actor_id            VARCHAR(128),
    actor_type          VARCHAR(48),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dai_assistance_event
    ON daidzai.dai_assistance_event (assistance_id, occurred_at);

-- Idempotent money projection: one row per applied mushe contribution event.
CREATE TABLE daidzai.dai_assistance_contribution_seen (
    contribution_id     UUID PRIMARY KEY,
    assistance_id       UUID NOT NULL REFERENCES daidzai.dai_assistance_request (id) ON DELETE CASCADE,
    amount              NUMERIC(14,2) NOT NULL,
    is_anonymous        BOOLEAN NOT NULL DEFAULT FALSE,
    contributor_label   VARCHAR(255),           -- display label only; never identity
    message             VARCHAR(512),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dai_assistance_seen ON daidzai.dai_assistance_contribution_seen (assistance_id);
