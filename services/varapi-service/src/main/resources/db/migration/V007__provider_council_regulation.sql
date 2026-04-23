-- =============================================
-- Service : varapi-service
-- Flyway  : V007__provider_council_regulation.sql
-- Purpose : Council-regulated provider lifecycle foundation:
--           council profiles, payment obligations (MusheX-linked),
--           Fundo identity links, council reviews, Fundo→CPD candidates,
--           application council linkage.
-- =============================================

ALTER TABLE varapi.provider_applications
    ADD COLUMN IF NOT EXISTS council_id BIGINT REFERENCES varapi.councils(id);

ALTER TABLE varapi.provider_applications
    ADD COLUMN IF NOT EXISTS review_state VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_provider_applications_council
    ON varapi.provider_applications(council_id);

-- -----------------------------------------------------------------
-- Provider ↔ council operational profile (per council relationship)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS varapi.provider_council_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT      NOT NULL REFERENCES varapi.councils(id),
    registration_category   VARCHAR(80),
    professional_class      VARCHAR(80),
    status                  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    primary_council_flag    BOOLEAN     NOT NULL DEFAULT false,
    start_date              DATE,
    end_date                DATE,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider_id, council_id)
);

CREATE INDEX IF NOT EXISTS idx_provider_council_profiles_provider
    ON varapi.provider_council_profiles(provider_id);

-- -----------------------------------------------------------------
-- Payment obligations (MusheX payment intent is financial truth)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS varapi.provider_payment_obligations (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    application_id          BIGINT REFERENCES varapi.provider_applications(id),
    council_id              BIGINT REFERENCES varapi.councils(id),
    obligation_type         VARCHAR(40) NOT NULL,
    amount                  NUMERIC(14, 2) NOT NULL,
    currency                VARCHAR(3)  NOT NULL DEFAULT 'USD',
    due_date                DATE,
    status                  VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    mushex_intent_id        VARCHAR(40),
    idempotency_key         VARCHAR(255) NOT NULL,
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_provider_payment_obligations_provider
    ON varapi.provider_payment_obligations(provider_id);
CREATE INDEX IF NOT EXISTS idx_provider_payment_obligations_application
    ON varapi.provider_payment_obligations(application_id);

-- -----------------------------------------------------------------
-- Impilo Fundo / Moodle identity link (learning platform anchor)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS varapi.fundo_learning_links (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    fundo_user_ref          VARCHAR(255) NOT NULL,
    link_status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    linked_at               TIMESTAMPTZ,
    last_sync_at            TIMESTAMPTZ,
    sync_state              VARCHAR(40),
    metadata                JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider_id)
);

-- -----------------------------------------------------------------
-- Council staff review / decision audit rows
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS varapi.provider_council_reviews (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    application_id          BIGINT REFERENCES varapi.provider_applications(id),
    council_id              BIGINT REFERENCES varapi.councils(id),
    review_type             VARCHAR(50) NOT NULL,
    reviewer_actor_id       VARCHAR(255) NOT NULL,
    decision                VARCHAR(40),
    notes                   TEXT,
    reviewed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolution_reference    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_provider_council_reviews_application
    ON varapi.provider_council_reviews(application_id);

-- -----------------------------------------------------------------
-- Fundo completion → governed CPD candidate (not auto-accepted CPD)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS varapi.fundo_cpd_candidates (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    provider_id             BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id              BIGINT REFERENCES varapi.councils(id),
    course_id               VARCHAR(120) NOT NULL,
    course_name             VARCHAR(512),
    completed_at            TIMESTAMPTZ NOT NULL,
    credits_suggested       INTEGER NOT NULL DEFAULT 0,
    verification_state      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    external_ref            VARCHAR(255) NOT NULL,
    linked_cpd_event_id     BIGINT,
    raw_payload             JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider_id, external_ref)
);

CREATE INDEX IF NOT EXISTS idx_fundo_cpd_candidates_provider
    ON varapi.fundo_cpd_candidates(provider_id);
