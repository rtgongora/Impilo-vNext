-- ============================================================================
-- coverage-service V001 — Initial schema
-- Provides: event_outbox (v1.1 envelope), idempotency_keys, coverage entities
-- ============================================================================

-- v1.1 Event Outbox (canonical schema per EVENTING_AND_TOPICS.md §2.2)
CREATE TABLE cv_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'coverage-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_cv_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_cv_outbox_unpublished
    ON cv_event_outbox (created_at)
    WHERE published_at IS NULL;

-- Idempotency keys (keyed by tenant_id + pod_id + idempotency_key per tech-companion spec)
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_cv_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- Coverage Plans — insurance/coverage plan definitions
CREATE TABLE cv_coverage_plans (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    plan_code       VARCHAR(64)     NOT NULL,
    plan_name       VARCHAR(255)    NOT NULL,
    payer_id        VARCHAR(255)    NOT NULL, -- insurance company / funder ID
    plan_type       VARCHAR(32)     NOT NULL, -- MEDICAL_AID, GOVERNMENT, PRIVATE, DONOR
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, TERMINATED
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    rules_json      JSONB,          -- coverage rules and limits
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_plan_code UNIQUE (tenant_id, plan_code)
);

CREATE INDEX idx_cv_plans_tenant ON cv_coverage_plans (tenant_id);
CREATE INDEX idx_cv_plans_payer ON cv_coverage_plans (payer_id);

-- Member Coverage — links clients to coverage plans
CREATE TABLE cv_member_coverage (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    client_id       VARCHAR(255)    NOT NULL, -- CRID or CPID
    plan_id         UUID            NOT NULL REFERENCES cv_coverage_plans(id),
    member_number   VARCHAR(64),
    relationship    VARCHAR(32)     NOT NULL DEFAULT 'SELF', -- SELF, SPOUSE, CHILD, DEPENDENT
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    effective_from  DATE            NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_member_client ON cv_member_coverage (client_id);
CREATE INDEX idx_cv_member_plan ON cv_member_coverage (plan_id);
CREATE INDEX idx_cv_member_tenant ON cv_member_coverage (tenant_id);

-- Pre-Authorization Requests
CREATE TABLE cv_preauth_requests (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id     UUID            NOT NULL REFERENCES cv_member_coverage(id),
    facility_id     VARCHAR(255)    NOT NULL,
    provider_id     VARCHAR(255)    NOT NULL,
    request_type    VARCHAR(32)     NOT NULL, -- PROCEDURE, ADMISSION, MEDICATION, DIAGNOSTIC
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, DENIED, EXPIRED
    clinical_info   JSONB,          -- clinical justification
    requested_items JSONB           NOT NULL, -- items requiring authorization
    decision_json   JSONB,          -- approval/denial details
    requested_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_preauth_tenant ON cv_preauth_requests (tenant_id);
CREATE INDEX idx_cv_preauth_coverage ON cv_preauth_requests (coverage_id);
CREATE INDEX idx_cv_preauth_status ON cv_preauth_requests (status) WHERE status = 'PENDING';

-- Claims
CREATE TABLE cv_claims (
    id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    coverage_id     UUID            NOT NULL REFERENCES cv_member_coverage(id),
    preauth_id      UUID            REFERENCES cv_preauth_requests(id),
    facility_id     VARCHAR(255)    NOT NULL,
    provider_id     VARCHAR(255)    NOT NULL,
    encounter_id    VARCHAR(255),
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, ADJUDICATED, PAID, REJECTED
    claim_type      VARCHAR(32)     NOT NULL, -- INPATIENT, OUTPATIENT, PHARMACY, DENTAL, OPTICAL
    line_items      JSONB           NOT NULL,
    total_amount    NUMERIC(12,2)   NOT NULL,
    approved_amount NUMERIC(12,2),
    adjudication    JSONB,          -- adjudication result details
    submitted_at    TIMESTAMPTZ,
    adjudicated_at  TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cv_claims_tenant ON cv_claims (tenant_id);
CREATE INDEX idx_cv_claims_coverage ON cv_claims (coverage_id);
CREATE INDEX idx_cv_claims_status ON cv_claims (status);
