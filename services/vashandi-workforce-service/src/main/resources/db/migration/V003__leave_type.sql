-- ============================================================================
-- vashandi V003 — Leave-type catalog (relocated from hr-payroll)
-- Vashandi owns the full workforce leave picture: types + balances + windows.
-- ============================================================================

CREATE TABLE vashandi.vsh_leave_type (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    annual_entitlement  INT          NOT NULL DEFAULT 0,
    carry_over_max      INT          NOT NULL DEFAULT 0,
    requires_approval   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_vsh_leave_type UNIQUE (tenant_id, name)
);

CREATE INDEX idx_vsh_leave_type_tenant ON vashandi.vsh_leave_type (tenant_id);
