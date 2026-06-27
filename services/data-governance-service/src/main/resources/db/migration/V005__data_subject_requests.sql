-- ============================================================================
-- data-governance-service V005 — Data-subject rights requests (GDPR Art. 17)
--
-- The Privacy-by-Architecture plane (§6) owns the lifecycle record for a data
-- subject's account-deletion / right-to-erasure request. The experience-bff is
-- stateless and only proxies here. Actual erasure (Keycloak deactivation, VITO
-- PII anonymisation, consent revocation) is performed by downstream consumers
-- of the emitted outbox event — this table is the authoritative request ledger
-- and lifecycle source of truth.
-- ============================================================================

CREATE TABLE dgv_data_subject_request (
    request_id      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    subject_id      VARCHAR(255)    NOT NULL,   -- Health ID / actor whose account is the subject
    request_type    VARCHAR(32)     NOT NULL DEFAULT 'ACCOUNT_DELETION',  -- ACCOUNT_DELETION (future: ACCESS, RECTIFICATION, RESTRICTION)
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | CANCELLED | REJECTED
    reason          TEXT,
    requested_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    cancelled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    correlation_id  VARCHAR(255)
);

CREATE INDEX idx_dgv_dsr_subject ON dgv_data_subject_request (tenant_id, subject_id, requested_at DESC);
CREATE INDEX idx_dgv_dsr_status  ON dgv_data_subject_request (tenant_id, status);

-- At most one in-flight (PENDING/PROCESSING) request of a given type per subject.
-- Partial unique index — runs against Postgres only (Flyway is disabled in H2 tests).
CREATE UNIQUE INDEX uq_dgv_dsr_active
    ON dgv_data_subject_request (tenant_id, subject_id, request_type)
    WHERE status IN ('PENDING', 'PROCESSING');
