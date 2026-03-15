-- Certification runs: tracks partner contract compliance checks
-- Table prefix: dvp_

CREATE TABLE dvp_certifications (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID            NOT NULL REFERENCES dvp_clients(id),
    tenant_id       UUID            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'RUNNING',
    result          VARCHAR(32),
    checks_total    INT             NOT NULL DEFAULT 0,
    checks_passed   INT             NOT NULL DEFAULT 0,
    checks_failed   INT             NOT NULL DEFAULT 0,
    report_json     TEXT,
    triggered_by    VARCHAR(255),
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dvp_certifications_client ON dvp_certifications (client_id);
CREATE INDEX idx_dvp_certifications_status ON dvp_certifications (status);
