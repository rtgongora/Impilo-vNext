-- =============================================================================
-- Tuso V034 — Facility governance cases: high-risk updates, transfer, dup
-- reports (FJ6/FJ7/FJ9, D-L; W8)
--
-- One append-only governance-case rail for the facility journeys that require
-- a steward decision rather than a self-service write:
--   * FJ6 high-risk field change (name/ownership/level/coordinates/legal
--     status/PIC/licence/closure) — low-risk fields self-serve directly;
--   * FJ7 ownership/management transfer — both parties independently verified;
--     the SAME facility identity is retained;
--   * FJ9 duplicate/incorrect/fake report — steward-only merge/redirect.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tuso.facility_governance_case (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    case_ref            VARCHAR(40)     NOT NULL UNIQUE,
    -- HIGH_RISK_UPDATE | TRANSFER | DUPLICATE_REPORT
    case_type           VARCHAR(24)     NOT NULL,
    facility_uuid       UUID            NOT NULL,
    raised_by           VARCHAR(128)    NOT NULL,
    payload             JSONB           NOT NULL,
    -- OPEN | APPROVED | REJECTED | MERGED | REDIRECTED
    status              VARCHAR(16)     NOT NULL DEFAULT 'OPEN',
    decided_by          VARCHAR(255),
    decided_at          TIMESTAMPTZ,
    decision_note       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_governance_case_type CHECK (case_type IN (
        'HIGH_RISK_UPDATE', 'TRANSFER', 'DUPLICATE_REPORT')),
    CONSTRAINT chk_governance_case_status CHECK (status IN (
        'OPEN', 'APPROVED', 'REJECTED', 'MERGED', 'REDIRECTED'))
);

COMMENT ON TABLE tuso.facility_governance_case IS
    'FJ6/FJ7/FJ9 governance cases (D-L): high-risk field changes, transfers and duplicate reports — steward-decided, never self-service writes to authoritative fields.';

CREATE INDEX IF NOT EXISTS idx_facility_governance_case_status
    ON tuso.facility_governance_case (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_facility_governance_case_facility
    ON tuso.facility_governance_case (tenant_id, facility_uuid);
