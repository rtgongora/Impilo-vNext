-- =====================================================================================
-- varapi :: V036 — HPA oversight escalation grants (ROM-W10, R8)
-- HPA oversight is aggregate reads + explicit per-case escalation grants — never standing council
-- operational access, never data copies (doctrine §11). A grant is the ONLY way HPA sees a
-- council's row-level case; the consolidated indicators are aggregate-only (reporting W9 OVERSIGHT
-- class). This table records the per-case grants.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.oversight_escalation_grant (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    council_id          BIGINT      NOT NULL REFERENCES varapi.councils(id),
    subject_type        VARCHAR(32) NOT NULL,
    subject_ref         VARCHAR(128) NOT NULL,
    granted_to_org_id   UUID        NOT NULL,
    reason              TEXT,
    granted_by          VARCHAR(128),
    valid_from          TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to            TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_oversight_subject_type CHECK (subject_type IN
        ('DISCIPLINARY_CASE', 'APPLICATION', 'APPEAL', 'PROVIDER'))
);
CREATE INDEX IF NOT EXISTS idx_oversight_grant_org
    ON varapi.oversight_escalation_grant(granted_to_org_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_oversight_grant_subject
    ON varapi.oversight_escalation_grant(tenant_id, subject_type, subject_ref);
