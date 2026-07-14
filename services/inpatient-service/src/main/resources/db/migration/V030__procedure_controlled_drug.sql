-- inpatient-service V030 — Controlled-drug LINK/PROJECTION (Wave 3, spec §9).
-- inventory-service (DURA) is the SoR for the controlled-drug register (two-person witness on issue /
-- administration / waste, running balance, reconciliation). This table holds ONLY the peer register-entry
-- id + a projection, keyed to the theatre case, and links an anaesthesia chart entry (Wave 1 MEDICATION
-- channel, controlled agent) to its register entry. Administering/wasting requires a witness (enforced
-- by the inventory register; the theatre side records the witness it sent).

CREATE TABLE IF NOT EXISTS inpatient.procedure_controlled_drug (
    record_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID NOT NULL,
    episode_id                UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    seq                       INT NOT NULL DEFAULT 1,
    inventory_register_entry_id VARCHAR(128),   -- inventory controlled-drug register entry id (SoR)
    chart_entry_id            UUID,             -- optional link to procedure_anaesthesia_chart_entry
    item_code                 VARCHAR(64),
    drug_name                 VARCHAR(255),
    action                    VARCHAR(16) NOT NULL,  -- ISSUE | ADMINISTER | WASTE | DISCARD
    quantity                  NUMERIC(12,3),
    unit                      VARCHAR(24),
    running_balance           NUMERIC(12,3),
    administering_provider    VARCHAR(128),
    witness_provider          VARCHAR(128),
    status                    VARCHAR(24) NOT NULL DEFAULT 'RECORDED', -- RECORDED | UNAVAILABLE
    idempotency_key           VARCHAR(128),
    created_by                VARCHAR(128),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_controlled_drug UNIQUE (tenant_id, episode_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_procedure_controlled_drug_episode
    ON inpatient.procedure_controlled_drug (episode_id, action);
