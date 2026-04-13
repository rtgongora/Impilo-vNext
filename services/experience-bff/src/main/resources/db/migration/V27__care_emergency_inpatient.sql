-- =============================================================================
-- V27: Care Pathways, Emergency Protocols, Inpatient Management
-- =============================================================================

-- ── Care Pathways ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS care_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    title           VARCHAR(255) NOT NULL,
    plan_type       VARCHAR(50) NOT NULL DEFAULT 'NURSING',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS care_plan_goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    care_plan_id    UUID NOT NULL REFERENCES care_plans(id) ON DELETE CASCADE,
    category        VARCHAR(50) NOT NULL DEFAULT 'CLINICAL',
    description     TEXT NOT NULL,
    target_value    VARCHAR(100),
    current_value   VARCHAR(100),
    priority        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    target_date     DATE,
    achieved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS care_plan_interventions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    care_plan_id    UUID NOT NULL REFERENCES care_plans(id) ON DELETE CASCADE,
    goal_id         UUID REFERENCES care_plan_goals(id),
    category        VARCHAR(50) NOT NULL DEFAULT 'ASSESSMENT',
    description     TEXT NOT NULL,
    frequency       VARCHAR(50),
    responsible_role VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_performed  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_care_plans_patient ON care_plans (tenant_id, patient_id);

-- ── Fluid Balance / I&O ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fluid_balance_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    record_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    entry_type      VARCHAR(10) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    volume_ml       INTEGER NOT NULL,
    description     VARCHAR(255),
    recorded_by     VARCHAR(255),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fluid_balance ON fluid_balance_records (tenant_id, patient_id, record_date);

-- ── Emergency Protocols ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS emergency_activations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    encounter_id    UUID,
    patient_id      UUID,
    protocol_type   VARCHAR(50) NOT NULL,
    activation_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    outcome         VARCHAR(50),
    team_leader     VARCHAR(255),
    location        VARCHAR(255),
    notes           TEXT,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS emergency_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES emergency_activations(id) ON DELETE CASCADE,
    action_type     VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    performed_by    VARCHAR(255),
    performed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS resuscitation_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES emergency_activations(id) ON DELETE CASCADE,
    cpr_cycles      INTEGER DEFAULT 0,
    defibrillations INTEGER DEFAULT 0,
    initial_rhythm  VARCHAR(50),
    final_rhythm    VARCHAR(50),
    rosc_achieved   BOOLEAN DEFAULT FALSE,
    rosc_time       TIMESTAMPTZ,
    medications     JSONB DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS apgar_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    minute          INTEGER NOT NULL,
    appearance      INTEGER NOT NULL CHECK (appearance BETWEEN 0 AND 2),
    pulse           INTEGER NOT NULL CHECK (pulse BETWEEN 0 AND 2),
    grimace         INTEGER NOT NULL CHECK (grimace BETWEEN 0 AND 2),
    activity        INTEGER NOT NULL CHECK (activity BETWEEN 0 AND 2),
    respiration     INTEGER NOT NULL CHECK (respiration BETWEEN 0 AND 2),
    total           INTEGER GENERATED ALWAYS AS (appearance + pulse + grimace + activity + respiration) STORED,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Early Warning Scores ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS early_warning_scores (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    score_type      VARCHAR(20) NOT NULL DEFAULT 'NEWS2',
    total_score     INTEGER NOT NULL,
    risk_level      VARCHAR(20) NOT NULL,
    components      JSONB NOT NULL,
    escalation_required BOOLEAN DEFAULT FALSE,
    recorded_by     VARCHAR(255),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ews ON early_warning_scores (tenant_id, patient_id, recorded_at DESC);

-- ── Inpatient: Admissions ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    bed_id          UUID,
    ward_id         UUID,
    admission_type  VARCHAR(50) NOT NULL DEFAULT 'EMERGENCY',
    admission_date  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    admitting_doctor VARCHAR(255),
    admitting_diagnosis TEXT,
    diet_orders     VARCHAR(100),
    activity_level  VARCHAR(50) DEFAULT 'BED_REST',
    isolation_type  VARCHAR(50),
    allergies_noted BOOLEAN DEFAULT FALSE,
    dvt_prophylaxis BOOLEAN DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    discharge_date  TIMESTAMPTZ,
    length_of_stay  INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admissions ON admissions (tenant_id, patient_id, status);

-- ── Ward Rounds ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ward_rounds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    ward_id         UUID NOT NULL,
    round_type      VARCHAR(50) NOT NULL DEFAULT 'MORNING',
    led_by          VARCHAR(255),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ward_round_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id        UUID NOT NULL REFERENCES ward_rounds(id) ON DELETE CASCADE,
    patient_id      UUID NOT NULL,
    bed_id          UUID,
    assessment      TEXT,
    plan            TEXT,
    new_orders      TEXT,
    escalation      VARCHAR(50),
    reviewed_by     VARCHAR(255),
    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Observation Charts ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS observation_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID,
    chart_type      VARCHAR(50) NOT NULL DEFAULT 'VITALS',
    parameters      JSONB NOT NULL,
    recorded_by     VARCHAR(255),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_observations ON observation_entries (tenant_id, patient_id, recorded_at DESC);

-- ── Patient Transfers ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS patient_transfers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    from_ward_id    UUID,
    from_bed_id     UUID,
    to_ward_id      UUID,
    to_bed_id       UUID,
    reason          VARCHAR(50) NOT NULL DEFAULT 'CLINICAL',
    clinical_notes  TEXT,
    transfer_type   VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    status          VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    requested_by    VARCHAR(255),
    accepted_by     VARCHAR(255),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);
