-- =============================================================================
-- WGV V010 — Employment CONFLICT adjudication case (IATG WS-D / Journey F).
--
-- When an EC-number match returns CONFLICT (duplicate EC, or an EC linked to a
-- different Health ID than the one supplied), a durable case is opened and — when
-- adjudication is enabled — an IATG identity-adjudication workflow is started. The
-- append-only decision (wgv_adjudication_decision) resolves this case in-process.
-- This table is mutable (status transitions); the decision record is the SoR.
-- =============================================================================

CREATE TABLE wgv_employment_conflict_case (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID          NOT NULL,
    ec_number                VARCHAR(40)   NOT NULL,               -- canonical EC in conflict
    supplied_health_id       VARCHAR(255),                         -- the claimant's Health ID (may be null)
    subject_ref              VARCHAR(255)  NOT NULL,               -- workflow subjectRef (health id, else EC)
    conflicting_record_count INT           NOT NULL DEFAULT 1,
    status                   VARCHAR(40)   NOT NULL DEFAULT 'OPEN', -- OPEN | UNDER_REVIEW | RESOLVED_APPROVED | RESOLVED_REJECTED
    workflow_instance_id     UUID,                                 -- adjudication join key (null while pending)
    adjudication_ref         VARCHAR(255),                         -- resolving decision id
    reason                   VARCHAR(500),
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by               VARCHAR(255),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_wgv_employment_conflict_case_status
        CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED_APPROVED','RESOLVED_REJECTED'))
);

CREATE INDEX idx_wgv_employment_conflict_case_ec
    ON wgv_employment_conflict_case (tenant_id, ec_number, status);
CREATE INDEX idx_wgv_employment_conflict_case_workflow
    ON wgv_employment_conflict_case (workflow_instance_id);
CREATE INDEX idx_wgv_employment_conflict_case_subject
    ON wgv_employment_conflict_case (tenant_id, subject_ref, status);

COMMENT ON TABLE wgv_employment_conflict_case IS
    'IATG WS-D: employment EC-number CONFLICT cases, opened on match conflict and resolved by an append-only adjudication decision.';
