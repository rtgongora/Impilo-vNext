-- tuso-service V026 — CSSD (Central Sterile Services Department) sterilisation cycles (Wave 3, spec §9).
-- TUSO owns the instrument_set REGISTRY and its sterilisation lifecycle. Wave 2 (V024) gave every set a
-- coarse sterilisation_state (STERILE | IN_USE | REPROCESSING | EXPIRED). Wave 3 adds:
--   * the full decontamination cycle the theatre demands: USE → SOILED → REPROCESSING → STERILISED →
--     STERILE, with sterile_until re-stamped on each successful autoclave run;
--   * a per-cycle audit log (who/when/autoclave load, contaminated / incomplete flags) so a set can be
--     traced through every decontamination.
-- inpatient holds only a LINK/PROJECTION (procedure_instrument_set_use, inpatient V028); TUSO is the SoR
-- for the set + its cycle history.

-- Widen the set state machine so a returned/soiled set is represented honestly (Wave 2 only allowed the
-- coarse four states). SOILED = returned dirty, awaiting reprocessing; STERILISED = autoclaved, cooling /
-- pre-release. STERILE remains the only theatre-issuable state.
ALTER TABLE tuso.instrument_set DROP CONSTRAINT IF EXISTS chk_instrument_state;
ALTER TABLE tuso.instrument_set
    ADD CONSTRAINT chk_instrument_state
    CHECK (sterilisation_state IN ('STERILE','IN_USE','SOILED','REPROCESSING','STERILISED','EXPIRED'));

-- Per-cycle audit log: one row per issue→...→sterile journey of a set.
CREATE TABLE IF NOT EXISTS tuso.instrument_set_cssd_cycle (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    instrument_set_id   UUID NOT NULL REFERENCES tuso.instrument_set(id) ON DELETE CASCADE,
    cycle_no            INTEGER NOT NULL,             -- monotonically increasing per set
    cycle_state         VARCHAR(24) NOT NULL DEFAULT 'USE',
        -- USE | SOILED | REPROCESSING | STERILISED | STERILE  (the CSSD decontamination FSM)
    -- Issue context (nullable: the set may be reprocessed without a linked theatre case).
    episode_ref         VARCHAR(128),                -- inpatient procedure_episode id the set was issued to
    facility_id         BIGINT REFERENCES tuso.facility(id),
    issued_to           VARCHAR(128),                -- actor / theatre the set was issued to
    issued_at           TIMESTAMPTZ,
    returned_at         TIMESTAMPTZ,
    -- Reprocessing context.
    autoclave_ref       VARCHAR(128),                -- autoclave / load reference
    operator            VARCHAR(128),                -- CSSD operator who ran the cycle
    reprocessing_at     TIMESTAMPTZ,
    sterilised_at       TIMESTAMPTZ,
    sterile_until       TIMESTAMPTZ,                 -- shelf-life expiry re-stamped on this cycle
    -- Safety flags — a contaminated or incomplete return blocks release back to STERILE.
    contaminated        BOOLEAN NOT NULL DEFAULT FALSE,
    incomplete          BOOLEAN NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cssd_cycle_state
        CHECK (cycle_state IN ('USE','SOILED','REPROCESSING','STERILISED','STERILE')),
    CONSTRAINT uq_cssd_cycle_no UNIQUE (tenant_id, instrument_set_id, cycle_no)
);
CREATE INDEX IF NOT EXISTS idx_cssd_cycle_set
    ON tuso.instrument_set_cssd_cycle (tenant_id, instrument_set_id, cycle_no DESC);
CREATE INDEX IF NOT EXISTS idx_cssd_cycle_episode
    ON tuso.instrument_set_cssd_cycle (episode_ref)
    WHERE episode_ref IS NOT NULL;
