-- =============================================================================
-- V28: Clinical Depth Enhancements — discharge clearances, resuscitation phases
-- =============================================================================

-- ── Discharge Clearances (staged workflow) ──────────────────────────
CREATE TABLE IF NOT EXISTS discharge_clearances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    encounter_id    UUID NOT NULL,
    patient_id      UUID NOT NULL,
    clearance_type  VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cleared_by      VARCHAR(255),
    cleared_at      TIMESTAMPTZ,
    waived          BOOLEAN NOT NULL DEFAULT FALSE,
    waived_reason   TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discharge_clearances ON discharge_clearances (tenant_id, encounter_id);

-- ── Resuscitation Phase Log ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS resuscitation_phases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES emergency_activations(id) ON DELETE CASCADE,
    phase           VARCHAR(30) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    cpr_cycles      INTEGER DEFAULT 0,
    defibrillations INTEGER DEFAULT 0,
    rhythm          VARCHAR(50),
    notes           TEXT
);

-- ── CPR Cycle Records ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cpr_cycles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES emergency_activations(id) ON DELETE CASCADE,
    cycle_number    INTEGER NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_secs   INTEGER,
    quality         VARCHAR(20) DEFAULT 'ADEQUATE',
    compressor      VARCHAR(255),
    notes           TEXT
);

-- ── Resuscitation Medications ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS resuscitation_medications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES emergency_activations(id) ON DELETE CASCADE,
    medication      VARCHAR(100) NOT NULL,
    dose            VARCHAR(50) NOT NULL,
    route           VARCHAR(20) NOT NULL DEFAULT 'IV',
    administered_by VARCHAR(255),
    administered_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
