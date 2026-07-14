-- Wave 1 clinical-safety — Specimen LINK/PROJECTION table (orchestration-by-reference).
-- Specimens named in the operative note are ordered through OROS (the SoR for the lab order,
-- specimen, and result), transported by NHUME, and their results (incl. critical escalation) are
-- projected back here. Pending pathology is EPISODE-scoped: it survives episode COMPLETED/discharge
-- and is only cleared by a real RESULTED/ACKNOWLEDGED/REJECTED state — never by an episode transition.
--
-- Written in the SAME transaction as the FSM mutation; peer calls carry an Idempotency-Key.

CREATE TABLE IF NOT EXISTS inpatient.procedure_specimen (
    specimen_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    episode_id           UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    kind                 VARCHAR(16) NOT NULL DEFAULT 'SPECIMEN',
    seq                  INT NOT NULL DEFAULT 1,
    specimen_label       VARCHAR(256),          -- parsed from the operative note
    specimen_type        VARCHAR(64),
    body_site            VARCHAR(128),
    -- Peer record ids (OROS = SoR).
    oros_order_id        VARCHAR(128),
    oros_specimen_id     VARCHAR(128),
    oros_result_id       VARCHAR(128),
    transport_id         UUID REFERENCES inpatient.procedure_transport(transport_id) ON DELETE SET NULL,
    -- Projection re-synced from OROS.
    status               VARCHAR(24) NOT NULL DEFAULT 'PARSED',
    -- PARSED | ORDERED | COLLECTED | DISPATCHED | RECEIVED | RESULTED | CRITICAL | ACKNOWLEDGED | REJECTED
    is_critical          BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at      TIMESTAMPTZ,
    acknowledged_by      VARCHAR(128),
    butano_document_ref  VARCHAR(256),          -- FHIR DocumentReference for the attached pathology
    idempotency_key      VARCHAR(128),
    created_by           VARCHAR(128),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_specimen UNIQUE (tenant_id, episode_id, kind, seq)
);

CREATE INDEX IF NOT EXISTS idx_procedure_specimen_episode
    ON inpatient.procedure_specimen (episode_id, status);
-- Pending pathology the reconciler must keep chasing (survives episode close).
CREATE INDEX IF NOT EXISTS idx_procedure_specimen_pending
    ON inpatient.procedure_specimen (tenant_id, status)
    WHERE status NOT IN ('RESULTED', 'ACKNOWLEDGED', 'REJECTED');
