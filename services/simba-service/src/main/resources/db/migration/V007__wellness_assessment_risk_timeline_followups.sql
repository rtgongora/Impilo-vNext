-- Simba wellness COMPLETION (Part 1 core): baseline assessment + deterministic risk scoring,
-- preventive reminders (Khuluma delivery seam), a person-facing wellness timeline, provider
-- follow-up actions, consent preferences (read by WellnessAccessGuard), and a handoff integration
-- ledger. All simba-schema. Simba owns wellness/prevention truth only; clinical/stock/learning/comms
-- stay with their owners (PCT/Dura/Fundo/Khuluma) — reminders + escalations record simba truth and
-- emit/handoff, never fake success.

-- ── Baseline wellness assessment (wizard header) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS simba.simba_assessment (
    id              BIGSERIAL PRIMARY KEY,
    assessment_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    template_code   VARCHAR(64) NOT NULL DEFAULT 'BASELINE_WELLNESS',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | IN_PROGRESS | COMPLETED | ABANDONED
    current_step    VARCHAR(48),
    consent_captured BOOLEAN NOT NULL DEFAULT FALSE,
    risk_band       VARCHAR(32),   -- LOW | MODERATE | HIGH | NEEDS_CLINICAL_REVIEW | URGENT_RED_FLAG
    risk_score      NUMERIC(6,2),
    risk_detail     JSONB,         -- per-rule contributions for transparency
    started_by      VARCHAR(128),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_assessment_person
    ON simba.simba_assessment (tenant_id, person_cpid, created_at DESC);

CREATE TABLE IF NOT EXISTS simba.simba_assessment_response (
    id              BIGSERIAL PRIMARY KEY,
    response_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    assessment_id   UUID NOT NULL REFERENCES simba.simba_assessment(assessment_id),
    section         VARCHAR(24) NOT NULL,  -- LIFESTYLE | MEASUREMENT | RISK_QUESTION
    question_code   VARCHAR(64) NOT NULL,
    value_text      VARCHAR(255),
    value_numeric   NUMERIC(12,3),
    unit            VARCHAR(24),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_assessment_response UNIQUE (assessment_id, question_code)
);
CREATE INDEX IF NOT EXISTS idx_simba_assessment_resp
    ON simba.simba_assessment_response (assessment_id, section);

-- ── Deterministic risk rule configuration (no fake AI — pure table-driven scoring) ────
CREATE TABLE IF NOT EXISTS simba.simba_risk_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    rule_code       VARCHAR(64) NOT NULL,
    template_code   VARCHAR(64) NOT NULL DEFAULT 'BASELINE_WELLNESS',
    question_code   VARCHAR(64) NOT NULL,
    operator        VARCHAR(8) NOT NULL,   -- GT | GTE | LT | LTE | EQ | IN | RANGE
    threshold_low   NUMERIC(12,3),
    threshold_high  NUMERIC(12,3),
    match_values    VARCHAR(255),          -- comma-separated set for IN / EQ (text)
    contributes_band VARCHAR(32) NOT NULL, -- band this rule pushes toward
    weight          NUMERIC(6,2) NOT NULL DEFAULT 1.0,
    red_flag        BOOLEAN NOT NULL DEFAULT FALSE,
    rationale       VARCHAR(255),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_risk_rule UNIQUE (tenant_id, template_code, rule_code)
);
CREATE INDEX IF NOT EXISTS idx_simba_risk_rule_tpl
    ON simba.simba_risk_rule (tenant_id, template_code, status);

-- ── Preventive reminders (screening/follow-up/habit/measurement). Khuluma delivers. ───
CREATE TABLE IF NOT EXISTS simba.simba_preventive_reminder (
    id              BIGSERIAL PRIMARY KEY,
    reminder_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    programme_id    UUID,
    reminder_type   VARCHAR(16) NOT NULL,  -- SCREENING | FOLLOW_UP | HABIT | MEASUREMENT
    title           VARCHAR(255) NOT NULL,
    detail          VARCHAR(512),
    due_at          TIMESTAMPTZ NOT NULL,
    cadence_months  INT,
    status          VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED | DUE | FIRED | SNOOZED | CANCELLED | COMPLETED
    channel         VARCHAR(24) NOT NULL DEFAULT 'PUSH',      -- PUSH | SMS | IN_APP
    source_ref      VARCHAR(128),
    last_fired_at   TIMESTAMPTZ,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_reminder_due
    ON simba.simba_preventive_reminder (status, due_at);
CREATE INDEX IF NOT EXISTS idx_simba_reminder_person
    ON simba.simba_preventive_reminder (tenant_id, person_cpid, status);

-- ── Person-facing wellness timeline (written synchronously inside each business txn) ──
CREATE TABLE IF NOT EXISTS simba.simba_timeline_event (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    event_category  VARCHAR(24) NOT NULL, -- ASSESSMENT|CHECK_IN|MEASUREMENT|GOAL|ENROLMENT|RISK_CHANGE|FOLLOW_UP|ESCALATION|COMMS|REMINDER
    title           VARCHAR(255) NOT NULL,
    summary         VARCHAR(512),
    severity        VARCHAR(16) NOT NULL DEFAULT 'INFO', -- INFO | POSITIVE | ATTENTION | URGENT
    ref_type        VARCHAR(48),
    ref_id          VARCHAR(128),
    detail          JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_timeline_person
    ON simba.simba_timeline_event (tenant_id, person_cpid, occurred_at DESC, id DESC);

-- ── Provider follow-up actions (from touchpoint / care-linkage / alert / assessment) ──
CREATE TABLE IF NOT EXISTS simba.simba_follow_up_action (
    id              BIGSERIAL PRIMARY KEY,
    follow_up_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    created_by_provider VARCHAR(128),
    origin_type     VARCHAR(24) NOT NULL,  -- TOUCHPOINT | CARE_LINKAGE | ALERT_REVIEW | ASSESSMENT
    origin_ref      VARCHAR(128),
    action_type     VARCHAR(48) NOT NULL,  -- CALL | REVIEW | SCREEN | EDUCATE | REFER | MEASURE
    title           VARCHAR(255) NOT NULL,
    detail          VARCHAR(512),
    due_date        DATE,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN | IN_PROGRESS | DONE | CANCELLED
    severity        VARCHAR(16) NOT NULL DEFAULT 'ROUTINE', -- ROUTINE | URGENT | EMERGENCY
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_follow_up_person
    ON simba.simba_follow_up_action (tenant_id, person_cpid, status);
CREATE INDEX IF NOT EXISTS idx_simba_follow_up_provider
    ON simba.simba_follow_up_action (tenant_id, created_by_provider, status);

-- ── Consent preferences (read by WellnessAccessGuard for sensitive/cross-person gating) ─
CREATE TABLE IF NOT EXISTS simba.simba_consent_preference (
    id              BIGSERIAL PRIMARY KEY,
    consent_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    caregiver_involvement  BOOLEAN NOT NULL DEFAULT FALSE,
    provider_involvement   BOOLEAN NOT NULL DEFAULT TRUE,
    programme_involvement  BOOLEAN NOT NULL DEFAULT FALSE,
    sensitive_categories   JSONB NOT NULL DEFAULT '[]'::jsonb, -- categories the person consents to share (e.g. MENTAL, SRH)
    data_sharing_scope     VARCHAR(24) NOT NULL DEFAULT 'CARE_TEAM', -- PRIVATE | CARE_TEAM | PROGRAMME | RESEARCH
    updated_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_consent_pref UNIQUE (tenant_id, person_cpid)
);

-- ── Integration handoff ledger (KHULUMA/FUNDO/OROS/NOMPILO/PCT). Never fake success. ──
CREATE TABLE IF NOT EXISTS simba.simba_integration_event (
    id              BIGSERIAL PRIMARY KEY,
    integration_event_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128),
    target_owner    VARCHAR(24) NOT NULL,  -- KHULUMA | FUNDO | OROS | NOMPILO | PCT
    intent          VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | DISPATCHED | ACKED | FAILED | UNSUPPORTED
    request_payload JSONB,
    response_ref    VARCHAR(128),
    error           VARCHAR(512),
    source_ref      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_integration_status
    ON simba.simba_integration_event (tenant_id, target_owner, status);

-- ── Seed deterministic baseline-wellness risk rules ───────────────────────────────────
INSERT INTO simba.simba_risk_rule (
    rule_id, tenant_id, rule_code, template_code, question_code, operator,
    threshold_low, threshold_high, match_values, contributes_band, weight, red_flag, rationale
) VALUES
-- Blood pressure (systolic mmHg)
('e0000006-0001-0000-0000-000000000001'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'BP_SEVERE', 'BASELINE_WELLNESS', 'systolic_bp', 'GTE', 180, NULL, NULL,
 'URGENT_RED_FLAG', 5.0, TRUE, 'Systolic BP >= 180 mmHg is a hypertensive-crisis red flag'),
('e0000006-0001-0000-0000-000000000002'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'BP_HIGH', 'BASELINE_WELLNESS', 'systolic_bp', 'RANGE', 140, 179.999, NULL,
 'HIGH', 3.0, FALSE, 'Systolic BP 140-179 mmHg indicates hypertension'),
('e0000006-0001-0000-0000-000000000003'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'BP_ELEVATED', 'BASELINE_WELLNESS', 'systolic_bp', 'RANGE', 130, 139.999, NULL,
 'MODERATE', 1.5, FALSE, 'Systolic BP 130-139 mmHg is elevated'),
-- BMI
('e0000006-0001-0000-0000-000000000004'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'BMI_OBESE', 'BASELINE_WELLNESS', 'bmi', 'GTE', 30, NULL, NULL,
 'HIGH', 2.0, FALSE, 'BMI >= 30 indicates obesity'),
('e0000006-0001-0000-0000-000000000005'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'BMI_OVERWEIGHT', 'BASELINE_WELLNESS', 'bmi', 'RANGE', 25, 29.999, NULL,
 'MODERATE', 1.0, FALSE, 'BMI 25-29.9 indicates overweight'),
-- Fasting glucose (mmol/L)
('e0000006-0001-0000-0000-000000000006'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'GLUCOSE_DIABETIC', 'BASELINE_WELLNESS', 'fasting_glucose', 'GTE', 11.1, NULL, NULL,
 'NEEDS_CLINICAL_REVIEW', 3.0, FALSE, 'Fasting glucose >= 11.1 mmol/L suggests diabetes — clinical review'),
('e0000006-0001-0000-0000-000000000007'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'GLUCOSE_PREDIABETIC', 'BASELINE_WELLNESS', 'fasting_glucose', 'RANGE', 6.1, 11.099, NULL,
 'MODERATE', 1.5, FALSE, 'Fasting glucose 6.1-11.0 mmol/L suggests impaired fasting glucose'),
-- Tobacco
('e0000006-0001-0000-0000-000000000008'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'TOBACCO_CURRENT', 'BASELINE_WELLNESS', 'tobacco_use', 'IN', NULL, NULL, 'DAILY,WEEKLY',
 'MODERATE', 1.5, FALSE, 'Current tobacco use raises cardiovascular and cancer risk'),
-- Mood / mental wellbeing (PHQ-style 0-27; red flag on self-harm question handled separately)
('e0000006-0001-0000-0000-000000000009'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'MOOD_SEVERE', 'BASELINE_WELLNESS', 'mood_score', 'GTE', 20, NULL, NULL,
 'NEEDS_CLINICAL_REVIEW', 3.0, FALSE, 'High mood-distress score warrants clinical review'),
('e0000006-0001-0000-0000-000000000010'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'SELF_HARM_FLAG', 'BASELINE_WELLNESS', 'self_harm_thoughts', 'EQ', NULL, NULL, 'YES',
 'URGENT_RED_FLAG', 5.0, TRUE, 'Reported self-harm thoughts are an urgent safety red flag'),
-- Waist circumference (cm)
('e0000006-0001-0000-0000-000000000011'::uuid, '00000000-0000-4000-8000-000000000001'::uuid,
 'WAIST_HIGH', 'BASELINE_WELLNESS', 'waist_cm', 'GTE', 102, NULL, NULL,
 'MODERATE', 1.0, FALSE, 'Elevated waist circumference raises metabolic risk')
ON CONFLICT (tenant_id, template_code, rule_code) DO NOTHING;
