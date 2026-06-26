-- Subsidy enrolment + annual-cap balance tracking (Coverage owns eligibility/subsidy).
--
-- V009 created cv_subsidy_programs (the standing programme + its annual_cap). This adds
-- the member<->subsidy LINK (enrolment) and the per-enrolment BALANCE that tracks
-- consumption against the annual cap, so eligibility checks can enforce the cap at
-- check time (Lane C build).

CREATE TABLE cv_subsidy_enrolments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subsidy_program_id  UUID NOT NULL REFERENCES cv_subsidy_programs (id),
    member_cpid         VARCHAR(80) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | ENDED
    -- Per-member cap override; falls back to the programme's annual_cap when null.
    annual_cap_override NUMERIC(14,2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    enrolled_by         VARCHAR(120),
    effective_from      DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cv_subsidy_enrolment UNIQUE (tenant_id, subsidy_program_id, member_cpid, effective_from),
    CONSTRAINT chk_cv_subsidy_enrolment_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED'))
);

CREATE INDEX idx_cv_subsidy_enrolment_member
    ON cv_subsidy_enrolments (tenant_id, member_cpid, status);
CREATE INDEX idx_cv_subsidy_enrolment_program
    ON cv_subsidy_enrolments (tenant_id, subsidy_program_id);

-- Annual-cap consumption per enrolment per cap year. Append-on-consume; the
-- consumed_amount is the running total of subsidy value drawn down in the period.
CREATE TABLE cv_subsidy_balances (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    enrolment_id        UUID NOT NULL REFERENCES cv_subsidy_enrolments (id),
    period_year         INT NOT NULL,           -- cap year, e.g. 2026
    cap_amount          NUMERIC(14,2),          -- snapshot of effective cap for the period
    consumed_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cv_subsidy_balance UNIQUE (enrolment_id, period_year)
);

CREATE INDEX idx_cv_subsidy_balance_enrolment
    ON cv_subsidy_balances (enrolment_id, period_year);
