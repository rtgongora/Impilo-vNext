-- inpatient-service V028 — Instrument-set use LINK/PROJECTION (Wave 3, spec §9).
-- TUSO owns the instrument_set registry + its CSSD sterilisation lifecycle (tuso V026). This table holds
-- ONLY the peer id + a projection of the issue/return status, so the theatre INSTRUMENT_SET readiness
-- gate consumes a REAL STERILE set (TUSO issue → IN_USE) and the SIGN_OUT/return flow marks it SOILED.

CREATE TABLE IF NOT EXISTS inpatient.procedure_instrument_set_use (
    use_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    episode_id              UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    seq                     INT NOT NULL DEFAULT 1,
    tuso_instrument_set_id  VARCHAR(128) NOT NULL,   -- TUSO instrument_set id (SoR)
    set_name                VARCHAR(255),
    issue_status            VARCHAR(24) NOT NULL DEFAULT 'ISSUED',  -- ISSUED | RETURNED | UNAVAILABLE
    sterile_until           TIMESTAMPTZ,
    issued_at               TIMESTAMPTZ,
    returned_at             TIMESTAMPTZ,
    contaminated            BOOLEAN NOT NULL DEFAULT FALSE,
    incomplete              BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key         VARCHAR(128),
    created_by              VARCHAR(128),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_instrument_set_use UNIQUE (tenant_id, episode_id, tuso_instrument_set_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_procedure_instrument_set_use_episode
    ON inpatient.procedure_instrument_set_use (episode_id, issue_status);
