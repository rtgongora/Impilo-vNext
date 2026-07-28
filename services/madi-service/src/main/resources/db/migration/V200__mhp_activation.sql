-- Massive Haemorrhage Protocol activation + pack tracking (W8a).
--
-- PROVEN NOT A DUPLICATE BEFORE WRITING THIS: BloodOrderService.emergencyRelease (G1.14) already
-- releases O-negative/uncrossmatched blood under EmergencyAccessGuard break-glass — but it operates
-- on ONE existing blood_orders row. It is the release mechanic, not a protocol. MHP is a distinct,
-- genuinely absent concept: a coordinated, ratio-based, MULTI-unit pack release triggered as one
-- protocol-level event, often before any individual blood_orders row exists at all — the whole point
-- of activating MHP is to bypass the per-order workflow in the acute phase. This migration adds the
-- protocol layer; every pack issued under it reuses the SAME EmergencyAccessGuard break-glass gate
-- BloodOrderService already enforces, not a second authorisation mechanism.
--
-- patient_cpid is NULLABLE here, deliberately diverging from blood_orders.patient_cpid (NOT NULL,
-- V001). MHP is activated for exactly the scenario this whole pack treats as normal, not exceptional:
-- a catastrophically bleeding patient whose identity is not yet resolved. Requiring an identified
-- patient before a protocol exists to stop the patient bleeding to death would be the same "identity
-- before emergency" defect this pack has refused everywhere else.

CREATE TABLE madi_mhp_activation (
    activation_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    facility_id         UUID,

    patient_cpid        VARCHAR(128),
    -- Cross-service correlations, plain ids (no FK — different services/databases), same pattern
    -- blood_orders.trauma_episode_id (V015) already uses.
    trauma_episode_id   UUID,
    emergency_episode_id UUID,

    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    activated_by        VARCHAR(128) NOT NULL,
    activated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Validated the same way EmergencyAccessGuard validates it at the application layer
    -- (EMERGENCY/BREAK_GLASS only) — a schema backstop for the same rule, not a second rule.
    purpose_of_use      VARCHAR(32) NOT NULL,

    stood_down_by       VARCHAR(128),
    stood_down_at       TIMESTAMPTZ,
    stand_down_reason   TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhp_status CHECK (status IN ('ACTIVE', 'STOOD_DOWN')),
    CONSTRAINT chk_mhp_purpose CHECK (purpose_of_use IN ('EMERGENCY', 'BREAK_GLASS')),
    CONSTRAINT chk_mhp_standdown_pair CHECK (
        status <> 'STOOD_DOWN' OR (
            stood_down_by IS NOT NULL AND stood_down_at IS NOT NULL
            AND stand_down_reason IS NOT NULL AND btrim(stand_down_reason) <> ''))
);

CREATE INDEX idx_mhp_activation_tenant_status
    ON madi_mhp_activation (tenant_id, status);
CREATE INDEX idx_mhp_activation_trauma_episode
    ON madi_mhp_activation (trauma_episode_id) WHERE trauma_episode_id IS NOT NULL;
CREATE INDEX idx_mhp_activation_emergency_episode
    ON madi_mhp_activation (emergency_episode_id) WHERE emergency_episode_id IS NOT NULL;

-- DELIBERATELY NO anti-duplicate-activation constraint. A partial unique keyed on trauma_episode_id
-- or patient_cpid would need to COALESCE across three nullable correlation columns (an unidentified
-- patient has none of them reliably), and a second genuinely-legitimate activation for a different
-- bleeding source during one long resuscitation is a real clinical scenario, not a defect — the same
-- reasoning that kept order-set re-invocation unconstrained in V206 (pct, W7b). Considered and
-- deferred, not omitted by oversight.

CREATE TABLE madi_mhp_pack (
    pack_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    activation_id       UUID        NOT NULL REFERENCES madi_mhp_activation (activation_id),

    pack_number         INT         NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    units_issued        INT         NOT NULL,
    blood_group         VARCHAR(8),

    issued_by           VARCHAR(128) NOT NULL,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhp_pack_component CHECK (
        component_type IN ('RBC', 'FFP', 'PLATELETS', 'CRYOPRECIPITATE')),
    CONSTRAINT chk_mhp_pack_units_positive CHECK (units_issued > 0),
    CONSTRAINT chk_mhp_pack_number_positive CHECK (pack_number > 0),
    -- One row per (activation, pack_number, component_type) — the same pack number issuing the same
    -- component twice would be a duplicate submission of the same clinical event, not a second real
    -- issuance (a genuinely new issuance is the next pack_number).
    CONSTRAINT uq_mhp_pack_activation_number_component UNIQUE (activation_id, pack_number, component_type)
);

CREATE INDEX idx_mhp_pack_activation
    ON madi_mhp_pack (activation_id, pack_number);

COMMENT ON TABLE madi_mhp_activation IS
    'Massive Haemorrhage Protocol activation — the protocol-level event, distinct from '
    'BloodOrderService.emergencyRelease which releases a single existing blood_orders row. Every '
    'pack issued under an activation reuses the SAME EmergencyAccessGuard break-glass gate; this is '
    'not a second authorisation mechanism.';
COMMENT ON COLUMN madi_mhp_activation.patient_cpid IS
    'Nullable, deliberately: MHP is activated for exactly the scenario this pack treats as normal — '
    'a catastrophically bleeding patient whose identity is not yet resolved. Diverges from '
    'blood_orders.patient_cpid (NOT NULL) on purpose.';
COMMENT ON TABLE madi_mhp_pack IS
    'One coordinated release of a blood component under an active MHP activation. Ratio compliance '
    '(e.g. 1:1:1 RBC:FFP:platelets) is read by summing units_issued per component within an '
    'activation — not separately tracked, so there is one source of truth for what was actually '
    'issued, not a duplicate running tally that could drift from it.';
