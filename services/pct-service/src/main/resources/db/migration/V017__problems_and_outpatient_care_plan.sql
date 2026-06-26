-- Problems List + Outpatient Care Plan (PCT).
-- Parity with inpatient-service's CarePlan/Goal model, but owned by PCT for the OUTPATIENT context.
-- PCT is the SoR for the outpatient encounter; the problems list and OPD care plan attach to a journey/encounter.

-- Problems list ---------------------------------------------------------------
CREATE TABLE pct_problems (
    problem_id        UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    subject_cpid      VARCHAR(128)    NOT NULL,
    journey_id        VARCHAR(26),
    encounter_id      VARCHAR(64),
    code              VARCHAR(64),                 -- terminology code (ICD-10/SNOMED) when coded
    code_system       VARCHAR(64),
    display           VARCHAR(512)    NOT NULL,    -- human-readable problem statement
    clinical_status   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | INACTIVE | RESOLVED
    category          VARCHAR(32)     NOT NULL DEFAULT 'DIAGNOSIS', -- DIAGNOSIS | SYMPTOM | RISK | SOCIAL
    onset_date        DATE,
    recorded_by       VARCHAR(255)    NOT NULL,
    resolved_at       TIMESTAMPTZ,
    notes             TEXT,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_problems_subject ON pct_problems (tenant_id, subject_cpid);
CREATE INDEX idx_pct_problems_journey ON pct_problems (tenant_id, journey_id);
CREATE INDEX idx_pct_problems_status ON pct_problems (tenant_id, subject_cpid, clinical_status);

-- Outpatient care plan --------------------------------------------------------
CREATE TABLE pct_care_plans (
    plan_id           UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    subject_cpid      VARCHAR(128)    NOT NULL,
    journey_id        VARCHAR(26),
    encounter_id      VARCHAR(64),
    title             VARCHAR(255)    NOT NULL,
    plan_type         VARCHAR(64)     NOT NULL DEFAULT 'OUTPATIENT',
    status            VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ON_HOLD | COMPLETED | CANCELLED
    created_by        VARCHAR(255),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_care_plans_subject ON pct_care_plans (tenant_id, subject_cpid);
CREATE INDEX idx_pct_care_plans_journey ON pct_care_plans (tenant_id, journey_id);

CREATE TABLE pct_care_plan_goals (
    goal_id           UUID            PRIMARY KEY,
    plan_id           UUID            NOT NULL REFERENCES pct_care_plans(plan_id) ON DELETE CASCADE,
    tenant_id         UUID            NOT NULL,
    category          VARCHAR(32)     NOT NULL DEFAULT 'CLINICAL',
    description       VARCHAR(1024)   NOT NULL,
    target_value      VARCHAR(255),
    current_value     VARCHAR(255),
    priority          VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM',
    status            VARCHAR(32)     NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | ACHIEVED | NOT_ACHIEVED | CANCELLED
    target_date       DATE,
    achieved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_care_plan_goals_plan ON pct_care_plan_goals (plan_id);

COMMENT ON TABLE pct_problems IS 'Outpatient problems list (PCT). Active/resolved clinical problems per subject.';
COMMENT ON TABLE pct_care_plans IS 'Outpatient care plan (PCT) — parity with inpatient-service CarePlan for the OPD context.';
COMMENT ON TABLE pct_care_plan_goals IS 'Goals attached to an outpatient care plan.';
