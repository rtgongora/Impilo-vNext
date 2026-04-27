-- Mvumo — Digital Consent Orchestration (schema: mvumo)
SET search_path TO public;

CREATE SCHEMA IF NOT EXISTS mvumo;

-- Templates (versioned, language, assurance, allowed methods)
CREATE TABLE mvumo.consent_template (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    template_key       VARCHAR(160) NOT NULL,
    version            INT          NOT NULL,
    language           VARCHAR(16)  NOT NULL DEFAULT 'en',
    consent_type       VARCHAR(96)  NOT NULL,
    required_assurance VARCHAR(32)   NOT NULL,
    title              VARCHAR(512) NOT NULL,
    body_markdown      TEXT         NOT NULL,
    allowed_methods    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    zibo_concept_code  VARCHAR(128),
    remote_allowed     BOOLEAN      NOT NULL DEFAULT true,
    offline_allowed    BOOLEAN      NOT NULL DEFAULT true,
    effective_from     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retired_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_mvumo_template_key_ver UNIQUE (tenant_id, template_key, version, language)
);

CREATE INDEX idx_mvumo_template_tenant ON mvumo.consent_template (tenant_id, template_key, language);

-- Product-facing consent request (lifecycle, context, not raw biometrics)
CREATE TABLE mvumo.consent_request (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    state                VARCHAR(64)  NOT NULL,
    subject_patient_ref  VARCHAR(255) NOT NULL,
    consent_type         VARCHAR(96)  NOT NULL,
    template_id          UUID         REFERENCES mvumo.consent_template (id),
    required_assurance   VARCHAR(32)   NOT NULL,
    context_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    workflow_ref         VARCHAR(255),
    encounter_ref        VARCHAR(255),
    actual_method        VARCHAR(64),
    actual_assurance     VARCHAR(32),
    proof_ref            VARCHAR(512),
    explainer_ref        VARCHAR(255),
    granted_by_ref       VARCHAR(255),
    refused_reason       TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ
);

CREATE INDEX idx_mvumo_req_patient ON mvumo.consent_request (tenant_id, subject_patient_ref);
CREATE INDEX idx_mvumo_req_encounter ON mvumo.consent_request (tenant_id, encounter_ref);

-- Remote / token / USSD / deep-link session (short-lived; token stored hashed)
CREATE TABLE mvumo.remote_consent_session (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    consent_request_id  UUID         NOT NULL REFERENCES mvumo.consent_request (id) ON DELETE CASCADE,
    state               VARCHAR(64)  NOT NULL,
    channel             VARCHAR(32)  NOT NULL,
    token_hash          VARCHAR(128) NOT NULL,
    open_count          INT          NOT NULL DEFAULT 0,
    max_uses            INT          NOT NULL DEFAULT 1,
    expires_at          TIMESTAMPTZ  NOT NULL,
    last_open_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_mvumo_remote_token ON mvumo.remote_consent_session (token_hash);
CREATE INDEX idx_mvumo_remote_req ON mvumo.remote_consent_session (consent_request_id);

-- Transactional outbox
CREATE TABLE mvumo.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(96)  NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_mvumo_outbox_unpub ON mvumo.event_outbox (created_at) WHERE published_at IS NULL;

-- Audit index records (summary; full audit in Tshepo audit when integrated)
CREATE TABLE mvumo.consent_event (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    consent_id      UUID         NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    actor_ref       VARCHAR(255) NOT NULL,
    detail          JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_mvumo_ev_consent ON mvumo.consent_event (consent_id);

COMMENT ON SCHEMA mvumo IS 'Mvumo — adaptive digital consent orchestration (product layer).';
COMMENT ON TABLE mvumo.consent_template IS 'Versioned templates; no patient PII in title/body in production use — bind via Zibo where possible.';
