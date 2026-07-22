-- =====================================================================================
-- varapi :: V031 — Application correspondence duality + RFI loop (ROM-W4, R7)
-- Adds the two-way case layer over the existing provider_applications FSM (V005): an RFI
-- request/respond/close loop (mirroring tuso's application_information_request shape) and a
-- case-message thread with an APPLICANT-vs-INTERNAL visibility split. Internal assessments live
-- in the same case but never serialize into applicant-facing payloads (asserted by test).
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.application_information_request (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    application_id  BIGINT      NOT NULL REFERENCES varapi.provider_applications(id),
    requested_by    VARCHAR(128),
    message         TEXT        NOT NULL,
    due_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    response_notes  TEXT,
    responded_at    TIMESTAMPTZ,
    responded_by    VARCHAR(128),
    closed_at       TIMESTAMPTZ,
    closed_by       VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_air_status CHECK (status IN ('OPEN', 'RESPONDED', 'CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_air_application ON varapi.application_information_request(application_id);
CREATE INDEX IF NOT EXISTS idx_air_open ON varapi.application_information_request(application_id) WHERE status = 'OPEN';

CREATE TABLE IF NOT EXISTS varapi.application_case_message (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    application_id  BIGINT      NOT NULL REFERENCES varapi.provider_applications(id),
    direction       VARCHAR(12) NOT NULL,
    author_actor_id VARCHAR(128),
    author_capacity VARCHAR(48),
    visibility      VARCHAR(12) NOT NULL DEFAULT 'APPLICANT',
    body            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_acm_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_acm_visibility CHECK (visibility IN ('APPLICANT', 'INTERNAL'))
);
CREATE INDEX IF NOT EXISTS idx_acm_application ON varapi.application_case_message(application_id, created_at);
