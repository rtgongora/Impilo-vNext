-- WS#6 Inpatient Suite: deterioration escalation + response timer, linked to the EWS that triggered it.
-- An EWS at/above threshold raises an escalation with a response-due deadline. If a clinician does not
-- acknowledge/respond by the deadline, the escalation is OVERDUE and a Rito safety event is raised.

CREATE TABLE inpatient.deterioration_escalation (
    escalation_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    facility_id     UUID,
    ward_id         UUID,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    encounter_id    UUID,
    score_id        UUID,                              -- EWS that triggered this
    total_score     INTEGER     NOT NULL,
    risk_level      VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RAISED',  -- RAISED | ACKNOWLEDGED | RESPONDED | OVERDUE | CLOSED
    raised_by       VARCHAR(128),
    raised_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    response_due_at TIMESTAMPTZ NOT NULL,
    acknowledged_by VARCHAR(128),
    acknowledged_at TIMESTAMPTZ,
    responded_by    VARCHAR(128),
    responded_at    TIMESTAMPTZ,
    response_note   TEXT,
    rito_case_ref   VARCHAR(128),                      -- safety case opened on overdue (link, not owned)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_escalation_patient ON inpatient.deterioration_escalation (tenant_id, subject_cpid, raised_at DESC);
CREATE INDEX idx_escalation_open ON inpatient.deterioration_escalation (tenant_id, status, response_due_at)
    WHERE status IN ('RAISED', 'ACKNOWLEDGED');
CREATE INDEX idx_escalation_ward ON inpatient.deterioration_escalation (tenant_id, ward_id, status);
