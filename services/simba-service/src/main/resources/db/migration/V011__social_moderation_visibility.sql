-- Simba social MODERATION + SAFETY. SELF_HARM/ABUSE/safeguarding reports build a care_linkage (via
-- CareLinkageService) — never handled purely socially. care_linkage_id is stamped on the report.

CREATE TABLE IF NOT EXISTS simba.simba_content_report (
    id              BIGSERIAL PRIMARY KEY,
    report_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    reporter_cpid   VARCHAR(128) NOT NULL,
    subject_type    VARCHAR(16) NOT NULL,    -- POST | STATUS | COMMENT | REEL | PROFILE
    subject_id      UUID NOT NULL,
    subject_owner_cpid VARCHAR(128),
    reason          VARCHAR(24) NOT NULL,    -- MEDICAL_MISINFO|HARMFUL_ADVICE|ABUSE|HATE|SEXUAL|SELF_HARM|PRIVACY|SPAM
    detail          VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN | TRIAGED | ACTIONED | DISMISSED
    care_linkage_id UUID,                    -- set when safety-escalated (SELF_HARM/ABUSE/safeguarding)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_content_report_status
    ON simba.simba_content_report (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_simba_content_report_subject
    ON simba.simba_content_report (subject_type, subject_id);

CREATE TABLE IF NOT EXISTS simba.simba_moderation_action (
    id              BIGSERIAL PRIMARY KEY,
    action_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    report_id       UUID,
    subject_type    VARCHAR(16) NOT NULL,
    subject_id      UUID NOT NULL,
    moderator_cpid  VARCHAR(128),
    action_state    VARCHAR(16) NOT NULL,    -- VISIBLE|PENDING|HIDDEN|REMOVED|FLAGGED|ESCALATED|REJECTED|RESTORED
    rationale       VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_moderation_action_subject
    ON simba.simba_moderation_action (subject_type, subject_id, id DESC);

CREATE TABLE IF NOT EXISTS simba.simba_content_visibility_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    visibility_level VARCHAR(32) NOT NULL,   -- PRIVATE|CAREGIVER|CARE_TEAM|GROUP|COMMUNITY|PROGRAMME|FACILITY|PUBLIC_WITHIN_IMPILO|ANONYMOUS
    requires_consent BOOLEAN NOT NULL DEFAULT FALSE,
    sensitive_category VARCHAR(24),
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_visibility_rule UNIQUE (tenant_id, visibility_level, sensitive_category)
);
