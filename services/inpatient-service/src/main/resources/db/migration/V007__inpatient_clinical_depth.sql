-- Inpatient clinical depth: care plans, charting, MAR, handover, ward alerts.

-- Admission clinical metadata (mobile/BFF compatibility)
ALTER TABLE inpatient.admission ADD COLUMN IF NOT EXISTS admitting_diagnosis TEXT;
ALTER TABLE inpatient.admission ADD COLUMN IF NOT EXISTS admission_type VARCHAR(50) DEFAULT 'EMERGENCY';
ALTER TABLE inpatient.admission ADD COLUMN IF NOT EXISTS diet_orders VARCHAR(100) DEFAULT 'REGULAR';
ALTER TABLE inpatient.admission ADD COLUMN IF NOT EXISTS activity_level VARCHAR(50) DEFAULT 'BED_REST';

-- Transfer workflow (request / accept)
ALTER TABLE inpatient.transfer ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE inpatient.transfer ADD COLUMN IF NOT EXISTS reason VARCHAR(50) DEFAULT 'CLINICAL';
ALTER TABLE inpatient.transfer ADD COLUMN IF NOT EXISTS clinical_notes TEXT;
ALTER TABLE inpatient.transfer ADD COLUMN IF NOT EXISTS requested_by VARCHAR(128);
ALTER TABLE inpatient.transfer ADD COLUMN IF NOT EXISTS accepted_by VARCHAR(128);

-- Nursing care plans
CREATE TABLE inpatient.care_plan (
    plan_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    encounter_id    UUID,
    title           VARCHAR(255) NOT NULL,
    plan_type       VARCHAR(50) NOT NULL DEFAULT 'NURSING',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_care_plan_patient ON inpatient.care_plan (tenant_id, subject_cpid);

CREATE TABLE inpatient.care_plan_goal (
    goal_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID NOT NULL REFERENCES inpatient.care_plan(plan_id) ON DELETE CASCADE,
    category        VARCHAR(50) NOT NULL DEFAULT 'CLINICAL',
    description     TEXT NOT NULL,
    target_value    VARCHAR(100),
    current_value   VARCHAR(100),
    priority        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    target_date     DATE,
    achieved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inpatient.care_plan_intervention (
    intervention_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID NOT NULL REFERENCES inpatient.care_plan(plan_id) ON DELETE CASCADE,
    goal_id         UUID REFERENCES inpatient.care_plan_goal(goal_id),
    category        VARCHAR(50) NOT NULL DEFAULT 'ASSESSMENT',
    description     TEXT NOT NULL,
    frequency       VARCHAR(50),
    responsible_role VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_performed  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fluid balance
CREATE TABLE inpatient.fluid_balance_record (
    record_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    encounter_id    UUID,
    record_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    entry_type      VARCHAR(10) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    volume_ml       INTEGER NOT NULL,
    description     VARCHAR(255),
    recorded_by     VARCHAR(128),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fluid_balance_patient ON inpatient.fluid_balance_record (tenant_id, subject_cpid, record_date);

-- Observations / vitals / ward charts (diet, fit, turn, drug-chart rows)
CREATE TABLE inpatient.clinical_chart_entry (
    entry_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    encounter_id    UUID,
    chart_type      VARCHAR(50) NOT NULL,
    parameters_json TEXT NOT NULL,
    recorded_by     VARCHAR(128),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chart_entry_patient ON inpatient.clinical_chart_entry (tenant_id, subject_cpid, chart_type, recorded_at DESC);

-- Medication administration record
CREATE TABLE inpatient.mar_entry (
    mar_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    prescription_id VARCHAR(128),
    drug_name       VARCHAR(255) NOT NULL,
    dose            VARCHAR(100),
    route           VARCHAR(20),
    time_due        VARCHAR(20),
    time_given      VARCHAR(20),
    status          VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    administered_by VARCHAR(128),
    notes           TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mar_patient ON inpatient.mar_entry (tenant_id, subject_cpid, recorded_at DESC);

-- Early warning scores
CREATE TABLE inpatient.early_warning_score (
    score_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    encounter_id    UUID,
    score_type      VARCHAR(20) NOT NULL DEFAULT 'NEWS2',
    total_score     INTEGER NOT NULL,
    risk_level      VARCHAR(20) NOT NULL,
    components_json TEXT,
    escalation_required BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_by     VARCHAR(128),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ews_patient ON inpatient.early_warning_score (tenant_id, subject_cpid, recorded_at DESC);

-- Emergency protocols
CREATE TABLE inpatient.emergency_activation (
    activation_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64),
    admission_ref   UUID,
    encounter_id    UUID,
    protocol_type   VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    outcome         VARCHAR(50),
    team_leader     VARCHAR(128),
    location        VARCHAR(255),
    notes           TEXT,
    activation_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE TABLE inpatient.emergency_action (
    action_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES inpatient.emergency_activation(activation_id) ON DELETE CASCADE,
    action_type     VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    performed_by    VARCHAR(128),
    performed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inpatient.resuscitation_record (
    resus_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activation_id   UUID NOT NULL REFERENCES inpatient.emergency_activation(activation_id) ON DELETE CASCADE,
    cpr_cycles      INTEGER DEFAULT 0,
    defibrillations INTEGER DEFAULT 0,
    initial_rhythm  VARCHAR(50),
    final_rhythm    VARCHAR(50),
    rosc_achieved   BOOLEAN DEFAULT FALSE,
    rosc_time       TIMESTAMPTZ,
    medications_json TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- APGAR
CREATE TABLE inpatient.apgar_score (
    apgar_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    encounter_id    UUID,
    minute_mark     INTEGER NOT NULL,
    appearance      INTEGER NOT NULL CHECK (appearance BETWEEN 0 AND 2),
    pulse           INTEGER NOT NULL CHECK (pulse BETWEEN 0 AND 2),
    grimace         INTEGER NOT NULL CHECK (grimace BETWEEN 0 AND 2),
    activity        INTEGER NOT NULL CHECK (activity BETWEEN 0 AND 2),
    respiration     INTEGER NOT NULL CHECK (respiration BETWEEN 0 AND 2),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Shift handover / takeover
CREATE TABLE inpatient.shift_handover (
    handover_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    facility_id     UUID NOT NULL,
    ward_id         UUID,
    shift_id        VARCHAR(128),
    outgoing_staff  VARCHAR(128) NOT NULL,
    incoming_staff  VARCHAR(128),
    situation       TEXT,
    background      TEXT,
    assessment      TEXT,
    recommendation  TEXT,
    general_notes   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    taken_over_at   TIMESTAMPTZ,
    taken_over_by   VARCHAR(128)
);

CREATE TABLE inpatient.shift_handover_item (
    item_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_id     UUID NOT NULL REFERENCES inpatient.shift_handover(handover_id) ON DELETE CASCADE,
    admission_ref   UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    acuity          VARCHAR(20) NOT NULL DEFAULT 'STABLE',
    diagnosis       TEXT,
    vital_status    VARCHAR(20) DEFAULT 'STABLE',
    key_meds_json   TEXT,
    pending_tasks_json TEXT,
    patient_notes   TEXT
);

-- Citizen ward alerts
CREATE TABLE inpatient.ward_patient_alert (
    alert_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    subject_cpid    VARCHAR(64) NOT NULL,
    admission_ref   UUID,
    ward_id         UUID,
    bed_id          UUID,
    alert_type      VARCHAR(50) NOT NULL DEFAULT 'CALL_NURSE',
    message         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    acknowledged_by VARCHAR(128),
    acknowledged_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ward_alert_ward ON inpatient.ward_patient_alert (tenant_id, ward_id, status, created_at DESC);
