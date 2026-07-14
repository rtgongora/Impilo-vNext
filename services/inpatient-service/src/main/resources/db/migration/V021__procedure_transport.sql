-- Wave 1 clinical-safety — Transport LINK/PROJECTION table (orchestration-by-reference).
-- Patient movement (WARD_TO_THEATRE | THEATRE_TO_PACU | THEATRE_TO_WARD) and specimen legs
-- (THEATRE_TO_LAB) are fulfilled by NHUME (the SoR for the delivery). This table holds the peer
-- delivery id + a status projection re-synced from NHUME. Overdue legs (sla_due_at breach) are
-- surfaced as a SAFETY signal — a patient/specimen must never be silently lost.
--
-- Written in the SAME transaction as the FSM mutation; peer calls carry an Idempotency-Key.

CREATE TABLE IF NOT EXISTS inpatient.procedure_transport (
    transport_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    kind                VARCHAR(24) NOT NULL,   -- PATIENT_MOVEMENT | SPECIMEN
    leg                 VARCHAR(24) NOT NULL,   -- WARD_TO_THEATRE | THEATRE_TO_PACU | THEATRE_TO_WARD | THEATRE_TO_LAB
    seq                 INT NOT NULL DEFAULT 1,
    nhume_delivery_id   VARCHAR(128),           -- NHUME delivery id (owner SoR)
    nhume_delivery_type VARCHAR(32),            -- PATIENT | LAB_SAMPLE_PICKUP
    oros_specimen_ref   VARCHAR(128),           -- links a SPECIMEN leg to procedure_specimen.oros_specimen_id
    status              VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    -- REQUESTED | ASSIGNED | IN_TRANSIT | DELIVERED | OVERDUE | FAILED | CANCELLED | UNAVAILABLE
    sla_due_at          TIMESTAMPTZ,
    overdue             BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key     VARCHAR(128),
    created_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_transport UNIQUE (tenant_id, episode_id, leg, seq)
);

CREATE INDEX IF NOT EXISTS idx_procedure_transport_episode
    ON inpatient.procedure_transport (episode_id, status);
-- Open legs the reconciler polls for overdue/status sync.
CREATE INDEX IF NOT EXISTS idx_procedure_transport_open
    ON inpatient.procedure_transport (tenant_id, status)
    WHERE status NOT IN ('DELIVERED', 'FAILED', 'CANCELLED');
