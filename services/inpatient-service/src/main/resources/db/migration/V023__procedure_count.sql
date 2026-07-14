-- Wave 1 clinical-safety — Surgical count table (WHO Sign-Out swab/instrument/needle reconciliation).
-- Discrete baseline (opening) and closing counts per count type. A discrepancy (baseline != closing)
-- BLOCKS the WHO SIGN_OUT completion path unless explicitly reconciled or overridden with a reason,
-- and auto-raises a RITO RETAINED_ITEM sentinel incident (RITO = SoR for the safety case). rito_case_ref
-- stores the owner reference. This is the theatre-side count fact + the owner ref — never the SoR case.

CREATE TABLE IF NOT EXISTS inpatient.procedure_count (
    count_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    episode_id           UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    kind                 VARCHAR(16) NOT NULL,   -- SWAB | INSTRUMENT | NEEDLE
    seq                  INT NOT NULL DEFAULT 1,
    baseline_count       INT,
    closing_count        INT,
    discrepancy          INT,                    -- baseline_count - closing_count (0 = reconciled)
    reconciled           BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_note  TEXT,
    override             BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason      TEXT,
    rito_case_ref        VARCHAR(128),           -- RITO case id when a RETAINED_ITEM incident is raised
    recorded_by          VARCHAR(128),
    idempotency_key      VARCHAR(128),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_count UNIQUE (tenant_id, episode_id, kind, seq)
);

CREATE INDEX IF NOT EXISTS idx_procedure_count_episode
    ON inpatient.procedure_count (episode_id, kind);
-- Unreconciled counts that gate SIGN_OUT.
CREATE INDEX IF NOT EXISTS idx_procedure_count_unreconciled
    ON inpatient.procedure_count (tenant_id, episode_id)
    WHERE reconciled = FALSE AND override = FALSE;
