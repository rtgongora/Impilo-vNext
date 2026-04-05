-- V003: Contributions tracking and appeals workflow.

-- ── Member Contributions ─────────────────────────────────────────
CREATE TABLE cv_member_contributions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    member_id       UUID NOT NULL REFERENCES cv_member_coverage(id),
    plan_id         UUID NOT NULL REFERENCES cv_coverage_plans(id),
    amount          NUMERIC(14,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'DUE',
    paid_at         TIMESTAMPTZ,
    payment_ref     VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contributions_member ON cv_member_contributions (member_id);
CREATE INDEX idx_contributions_plan ON cv_member_contributions (plan_id, tenant_id);
CREATE INDEX idx_contributions_status ON cv_member_contributions (status);

COMMENT ON COLUMN cv_member_contributions.status IS 'DUE, PAID, OVERDUE, WAIVED, REFUNDED';

-- ── Appeals ──────────────────────────────────────────────────────
CREATE TABLE cv_appeals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    claim_id        VARCHAR(100) NOT NULL,
    coverage_id     UUID,
    appeal_reason   TEXT NOT NULL,
    appeal_evidence JSONB DEFAULT '{}',
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    decision        VARCHAR(30),
    decision_notes  TEXT,
    decided_by      VARCHAR(255),
    decided_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_appeals_claim ON cv_appeals (claim_id);
CREATE INDEX idx_appeals_status ON cv_appeals (status, tenant_id);

COMMENT ON COLUMN cv_appeals.status IS 'PENDING, UNDER_REVIEW, APPROVED, DENIED, EXPIRED';
COMMENT ON COLUMN cv_appeals.decision IS 'UPHELD, OVERTURNED, PARTIAL';

-- ── Membership admin endpoints support ───────────────────────────
-- Add list-by-plan query support
CREATE INDEX IF NOT EXISTS idx_member_coverage_plan ON cv_member_coverage (plan_id, tenant_id);
