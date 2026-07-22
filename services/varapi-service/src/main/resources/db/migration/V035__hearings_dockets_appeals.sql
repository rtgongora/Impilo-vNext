-- =====================================================================================
-- varapi :: V035 — Hearings, committee dockets, appeals (ROM-W8, R6)
-- The case-owning service owns the docket (org-registry owns the committee ORGAN + membership,
-- V008). A committee member sees ONLY cases docketed to them (docket-scoped visibility, enforced
-- here + authz V046 SHADOW). A member may not sit on a case whose subject is themselves (recusal).
-- Appeals are cases referencing the appealed determination.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.disciplinary_hearing (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    case_id         BIGINT      NOT NULL REFERENCES varapi.provider_disciplinary_cases(id),
    committee_ref   VARCHAR(128),
    status          VARCHAR(24) NOT NULL DEFAULT 'DOCKETED',
    scheduled_at    TIMESTAMPTZ,
    determination   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_hearing_status CHECK (status IN
        ('DOCKETED', 'SCHEDULED', 'SITTING_IN_PROGRESS', 'DELIBERATION', 'DETERMINATION_ISSUED', 'CONCLUDED'))
);
CREATE INDEX IF NOT EXISTS idx_hearing_case ON varapi.disciplinary_hearing(case_id);

CREATE TABLE IF NOT EXISTS varapi.case_docket_assignment (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    case_id                 BIGINT      NOT NULL REFERENCES varapi.provider_disciplinary_cases(id),
    committee_member_health_id VARCHAR(128) NOT NULL,
    committee_ref           VARCHAR(128),
    assigned_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_docket UNIQUE (tenant_id, case_id, committee_member_health_id)
);
CREATE INDEX IF NOT EXISTS idx_docket_member
    ON varapi.case_docket_assignment(tenant_id, committee_member_health_id) WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS varapi.disciplinary_appeal (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    case_id             BIGINT      NOT NULL REFERENCES varapi.provider_disciplinary_cases(id),
    appealed_decision   VARCHAR(128),
    grounds             TEXT,
    filed_by            VARCHAR(128),
    status              VARCHAR(24) NOT NULL DEFAULT 'FILED',
    filed_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_appeal_status CHECK (status IN ('FILED', 'UNDER_REVIEW', 'UPHELD', 'DISMISSED', 'REMITTED'))
);
CREATE INDEX IF NOT EXISTS idx_appeal_case ON varapi.disciplinary_appeal(case_id);
