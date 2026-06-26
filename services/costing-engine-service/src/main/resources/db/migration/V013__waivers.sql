-- Discretionary fee waivers (COSTA).
--
-- DISTINCT from costa_exemption_rules: an exemption rule is a *catalog rule*
-- (category + discount% applied automatically by ExemptionEngine.split during bill
-- splitting). A waiver is a *discretionary, per-patient / per-bill grant* with an
-- explicit approval lifecycle (GRANTED -> APPROVED | REJECTED -> REVOKED), a granting
-- and approving officer, and a justification. It expresses a one-off decision to waive
-- some or all of a specific charge — e.g. hardship, goodwill, emergency-deferred
-- write-off — not a standing policy. The two compose: a waiver may reference an
-- emergency-deferred charge (V012) as the thing being waived.

CREATE TABLE costa_waivers (
    waiver_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    waiver_reference    VARCHAR(40) NOT NULL,      -- human-facing reference, unique per tenant

    -- What is being waived.
    patient_cpid        VARCHAR(80),
    encounter_id        VARCHAR(80),
    bill_id             VARCHAR(26),
    charge_id           VARCHAR(26),
    deferred_charge_id  UUID,                       -- optional link to emergency reconciliation (V012)

    -- Amount / extent.
    waiver_type         VARCHAR(20) NOT NULL,       -- FULL | PARTIAL
    waived_amount       NUMERIC(14,2),              -- null for FULL (= whole charge)
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    reason_category     VARCHAR(40),                -- HARDSHIP | GOODWILL | EMERGENCY | POLICY | OTHER
    justification       TEXT NOT NULL,

    -- Approval lifecycle.
    status              VARCHAR(20) NOT NULL DEFAULT 'GRANTED',  -- GRANTED | APPROVED | REJECTED | REVOKED
    granted_by          VARCHAR(120) NOT NULL,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_by         VARCHAR(120),
    approved_at         TIMESTAMPTZ,
    rejected_reason     TEXT,
    revoked_by          VARCHAR(120),
    revoked_at          TIMESTAMPTZ,
    revoke_reason       TEXT,

    audit_reference     VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_costa_waiver_reference UNIQUE (tenant_id, waiver_reference),
    CONSTRAINT chk_costa_waiver_type CHECK (waiver_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT chk_costa_waiver_status
        CHECK (status IN ('GRANTED', 'APPROVED', 'REJECTED', 'REVOKED'))
);

CREATE INDEX idx_costa_waiver_tenant_status
    ON costa_waivers (tenant_id, status, created_at DESC);
CREATE INDEX idx_costa_waiver_tenant_patient
    ON costa_waivers (tenant_id, patient_cpid);
CREATE INDEX idx_costa_waiver_bill
    ON costa_waivers (tenant_id, bill_id);
