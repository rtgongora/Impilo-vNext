-- Wave 1 clinical-safety — Blood LINK/PROJECTION table (orchestration-by-reference).
-- The theatre episode drives the MADI blood pipeline (group&screen → crossmatch → reserve → issue →
-- NHUME transport → bedside two-step verify → transfuse → observe → react → return → reconcile), but
-- MADI remains the SoR for the blood order + transfusion episode. This table holds ONLY the peer
-- record ids + a status projection re-synced from MADI. No blood truth is owned here.
--
-- Every row is written in the SAME transaction as the FSM mutation on procedure_episode. Peer calls
-- carry an Idempotency-Key; the stored idempotency_key lets the reconciler stay idempotent.

CREATE TABLE IF NOT EXISTS inpatient.procedure_blood_link (
    link_id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                    UUID NOT NULL,
    episode_id                   UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    kind                         VARCHAR(16) NOT NULL DEFAULT 'BLOOD',
    seq                          INT NOT NULL DEFAULT 1,
    -- Peer record ids (MADI = SoR). Never fabricated: null until the owner returns them.
    madi_order_id                VARCHAR(128),   -- MADI blood order id
    madi_transfusion_episode_id  VARCHAR(128),   -- MADI transfusion episode id
    oros_blood_order_id          VARCHAR(128),   -- legacy OROS BLOOD_BANK routing ref (optional)
    nhume_delivery_id            VARCHAR(128),   -- NHUME delivery for BLOOD_PRODUCT transport
    -- Request facts (theatre-side).
    units_requested              INT,
    blood_group                  VARCHAR(8),
    component_type               VARCHAR(32),
    required                     BOOLEAN NOT NULL DEFAULT TRUE,
    -- Projections re-synced from MADI getOrderDetail (owner truth by read-through).
    reservation_status           VARCHAR(24),    -- null | PENDING | RESERVED
    crossmatch_status            VARCHAR(24),    -- null | PENDING | COMPATIBLE | INCOMPATIBLE
    status                       VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    -- REQUESTED | GROUP_SCREEN | CROSSMATCHED | RESERVED | ISSUED | IN_TRANSIT | TRANSFUSING |
    -- TRANSFUSED | REACTION | RETURNED | RECONCILED | NOT_REQUIRED | UNAVAILABLE
    idempotency_key              VARCHAR(128),
    created_by                   VARCHAR(128),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_blood_link UNIQUE (tenant_id, episode_id, kind, seq)
);

CREATE INDEX IF NOT EXISTS idx_procedure_blood_link_episode
    ON inpatient.procedure_blood_link (episode_id, status);
-- Open rows the reconciler polls (not yet in a terminal state).
CREATE INDEX IF NOT EXISTS idx_procedure_blood_link_open
    ON inpatient.procedure_blood_link (tenant_id, status)
    WHERE status NOT IN ('TRANSFUSED', 'RECONCILED', 'RETURNED', 'NOT_REQUIRED');
