-- =============================================================================
-- VITO V021 — Temp ID Supervisor Review Queue (Health OS §13)
--
-- "Temporary identifiers shall be visibly marked, minimally privileged,
--  reason-coded, time-bound, attributable to a responsible creator, and
--  subject to mandatory reconciliation, supervisory review, and audit."
-- =============================================================================

CREATE TABLE vito.temp_id_reviews (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(255)    NOT NULL,
    provisional_id          UUID            NOT NULL,
    temp_health_id          VARCHAR(255)    NOT NULL,
    reason_code             VARCHAR(64)     NOT NULL,
    reason_description      TEXT,
    created_by              VARCHAR(255)    NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ     NOT NULL,
    review_status           VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    reviewed_by             VARCHAR(255),
    reviewed_at             TIMESTAMPTZ,
    review_decision         VARCHAR(32),
    review_notes            TEXT,
    escalated               BOOLEAN         NOT NULL DEFAULT FALSE,
    escalated_at            TIMESTAMPTZ,
    escalated_to            VARCHAR(255),

    CONSTRAINT uq_review_provisional UNIQUE (tenant_id, provisional_id)
);

CREATE INDEX idx_temp_review_pending ON vito.temp_id_reviews (tenant_id, review_status)
    WHERE review_status = 'PENDING';
CREATE INDEX idx_temp_review_escalated ON vito.temp_id_reviews (tenant_id, escalated)
    WHERE escalated = TRUE AND review_status = 'PENDING';
