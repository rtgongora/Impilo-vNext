-- ============================================================================
-- vashandi V002 — Leave entitlement / balance
--
-- Vashandi is the SoR for workforce leave. It already records leave *windows*
-- (vsh_leave_availability); this adds entitlement/balance so a leave request can
-- be reserved against an annual entitlement (relocated from hr-payroll). Balance
-- is enforced only when a row exists for the (profile, leave_type, fiscal_year),
-- so existing operational leave windows are not broken.
-- ============================================================================

CREATE TABLE vashandi.vsh_leave_balance (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id             UUID         NOT NULL,
    workforce_profile_id  UUID         NOT NULL,
    leave_type            VARCHAR(64)  NOT NULL,
    fiscal_year           INT          NOT NULL,
    entitlement           INT          NOT NULL DEFAULT 0,
    used                  INT          NOT NULL DEFAULT 0,
    carried_over          INT          NOT NULL DEFAULT 0,
    available             INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_vsh_leave_balance UNIQUE (tenant_id, workforce_profile_id, leave_type, fiscal_year)
);

CREATE INDEX idx_vsh_leave_balance_profile
    ON vashandi.vsh_leave_balance (tenant_id, workforce_profile_id, fiscal_year);
