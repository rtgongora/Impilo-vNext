-- =============================================
-- Service : pct-service
-- Flyway  : V002__add_encounter_denorm_columns.sql
-- Purpose : Add denormalized and workflow columns
--           missing from V001 across multiple tables.
-- =============================================

-- ─── pct_encounters ───────────────────────────────────────────────────────────
ALTER TABLE pct_encounters
    ADD COLUMN encounter_ref   UUID,
    ADD COLUMN subject_cpid    VARCHAR(64),
    ADD COLUMN facility_id     UUID;

CREATE INDEX idx_pct_encounters_encounter_ref ON pct_encounters (encounter_ref);
CREATE INDEX idx_pct_encounters_facility_id ON pct_encounters (facility_id);

-- ─── pct_queue_items ──────────────────────────────────────────────────────────
ALTER TABLE pct_queue_items
    ADD COLUMN facility_id UUID;

-- ─── pct_admissions ───────────────────────────────────────────────────────────
ALTER TABLE pct_admissions
    ADD COLUMN facility_id    UUID,
    ADD COLUMN patient_cpid   VARCHAR(64),
    ADD COLUMN requested_by   VARCHAR(128),
    ADD COLUMN approved_by    VARCHAR(128),
    ADD COLUMN updated_at     TIMESTAMPTZ NOT NULL DEFAULT now();

-- ─── pct_transfers ────────────────────────────────────────────────────────────
ALTER TABLE pct_transfers
    ADD COLUMN admission_id   UUID,
    ADD COLUMN completed_by   VARCHAR(128),
    ADD COLUMN completed_at   TIMESTAMPTZ;

-- ─── pct_discharge_cases ──────────────────────────────────────────────────────
ALTER TABLE pct_discharge_cases
    ADD COLUMN facility_id       UUID,
    ADD COLUMN status            VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    ADD COLUMN summary_cleared   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN updated_at        TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_pct_discharge_cases_status ON pct_discharge_cases (facility_id, status);

-- ─── pct_death_cases ──────────────────────────────────────────────────────────
ALTER TABLE pct_death_cases
    ADD COLUMN facility_id    UUID,
    ADD COLUMN patient_cpid   VARCHAR(64),
    ADD COLUMN status         VARCHAR(30) NOT NULL DEFAULT 'RECORDED';

CREATE INDEX idx_pct_death_cases_status ON pct_death_cases (facility_id, status);

-- ─── pct_tasks ────────────────────────────────────────────────────────────────
ALTER TABLE pct_tasks
    ADD COLUMN created_by    VARCHAR(128),
    ADD COLUMN completed_by  VARCHAR(128);
