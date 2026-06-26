-- Emergency override deferred-charge reconciliation (Journey Law 1).
-- Emergency care is NEVER blocked by identity/payment gaps. When the access gate
-- returns EMERGENCY_OVERRIDE, care proceeds and charges accrue flagged-deferred.
-- This table is the post-care settle path: it lists emergency-overridden / deferred
-- charges, lets a provisional person be linked to a confirmed person without losing
-- the audit chain (VITO is SoR; this records the link, never overwrites), and routes
-- each deferred charge to SETTLE / CLAIM / WAIVER. Emitting EMERGENCY_DEFERRED_CHARGE
-- value-events onto core.transaction.events (C8) closes the reconciliation loop.
--
-- This is NOT a new system-of-record for charges (costa_charge_records owns that) nor
-- for the access gate (costa_service_access_decisions owns that). It is the
-- reconciliation workflow ledger over those two SoRs.

CREATE TABLE costa_emergency_deferred_charges (
    deferred_charge_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,

    -- Link back to the gate decision that authorised emergency care (audit chain anchor).
    service_access_decision_id  UUID REFERENCES costa_service_access_decisions (service_access_decision_id),
    -- Optional link to the concrete accrued charge once one exists.
    charge_id                   VARCHAR(26) REFERENCES costa_charge_records (charge_id),

    encounter_id                VARCHAR(80),
    facility_id                 UUID,

    -- Identity reconciliation: provisional person captured at emergency presentation,
    -- confirmed person resolved post-stabilisation. Both retained (link, not overwrite).
    provisional_person_ref      VARCHAR(128),
    confirmed_person_ref        VARCHAR(128),
    identity_reconciled         BOOLEAN NOT NULL DEFAULT FALSE,
    identity_reconciled_at      TIMESTAMPTZ,

    -- Monetary deferral.
    deferred_amount             NUMERIC(14, 2),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Reconciliation lifecycle.
    reconciliation_state        VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | RECONCILED | CANCELLED
    settlement_route            VARCHAR(20),                             -- SETTLE | CLAIM | WAIVER (null until routed)
    settlement_reference        VARCHAR(200),                            -- payment / claim-pack / waiver reference
    reconciliation_reason       TEXT,

    -- Audit / provenance.
    flagged_by                  VARCHAR(120),
    reconciled_by               VARCHAR(120),
    audit_reference             VARCHAR(120),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconciled_at               TIMESTAMPTZ,

    CONSTRAINT chk_costa_deferred_reconciliation_state
        CHECK (reconciliation_state IN ('PENDING', 'RECONCILED', 'CANCELLED')),
    CONSTRAINT chk_costa_deferred_settlement_route
        CHECK (settlement_route IS NULL OR settlement_route IN ('SETTLE', 'CLAIM', 'WAIVER'))
);

CREATE INDEX idx_costa_deferred_tenant_state
    ON costa_emergency_deferred_charges (tenant_id, reconciliation_state, created_at DESC);
CREATE INDEX idx_costa_deferred_tenant_encounter
    ON costa_emergency_deferred_charges (tenant_id, encounter_id);
CREATE INDEX idx_costa_deferred_provisional_person
    ON costa_emergency_deferred_charges (tenant_id, provisional_person_ref);
