-- Simba wellness DEPTH: structured plans/journeys + enrollment + plan tasks,
-- lightweight habit check-ins, coaching relationships/touchpoints, a general
-- cross-person/coaching access-audit trail, and wellness-to-care linkage tracking.
--
-- Simba is the WELLNESS / prevention layer. It owns wellness journeys, NOT stock
-- (Dura/inventory-service), NOT clinical records (Butano/PCT), NOT learning content
-- (Fundo), NOT notification delivery (Khuluma). Care routing records a linkage row and
-- requests a referral from PCT; results stay with the clinical owner.

-- ── Plan / journey catalogue (governed templates a person can enroll into) ────────────
CREATE TABLE IF NOT EXISTS simba.wellness_plans (
    id              BIGSERIAL PRIMARY KEY,
    plan_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    plan_code       VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    category        VARCHAR(48) NOT NULL,            -- ACTIVITY | NUTRITION | SLEEP | TOBACCO | CHRONIC_PREVENTION | MENTAL_WELLBEING | MATERNAL | YOUTH | WORKPLACE
    audience        VARCHAR(48) DEFAULT 'INDIVIDUAL',-- INDIVIDUAL | COHORT | PROGRAMME
    duration_days   INT,
    eligibility_min_age INT,
    eligibility_max_age INT,
    fundo_content_ref VARCHAR(128),                  -- Fundo (learning-service) course/resource ref; Simba links, never duplicates content
    clinical_caution BOOLEAN NOT NULL DEFAULT FALSE, -- TRUE → enrolment should surface a care/screening prompt
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_plan_code UNIQUE (tenant_id, plan_code)
);

-- Ordered stages/tasks that make up a plan template.
CREATE TABLE IF NOT EXISTS simba.plan_tasks (
    id              BIGSERIAL PRIMARY KEY,
    plan_task_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    plan_id         UUID NOT NULL REFERENCES simba.wellness_plans(plan_id),
    stage           INT NOT NULL DEFAULT 1,
    seq             INT NOT NULL DEFAULT 1,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    task_type       VARCHAR(32) NOT NULL DEFAULT 'HABIT',  -- HABIT | EDUCATION | CHECKIN | SCREENING | MILESTONE
    fundo_content_ref VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_plan_tasks_plan ON simba.plan_tasks (plan_id, stage, seq);

-- A person's enrollment into a plan.
CREATE TABLE IF NOT EXISTS simba.plan_enrollments (
    id              BIGSERIAL PRIMARY KEY,
    enrollment_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    plan_id         UUID NOT NULL REFERENCES simba.wellness_plans(plan_id),
    person_cpid     VARCHAR(128) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | WITHDRAWN
    progress_pct    INT NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    enrolled_by     VARCHAR(128),                            -- actor who enrolled (self, coach, programme admin)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_enrollment UNIQUE (plan_id, person_cpid)
);
CREATE INDEX IF NOT EXISTS idx_simba_enroll_person ON simba.plan_enrollments (tenant_id, person_cpid, status);

-- Per-enrollment task progress (instantiated from plan_tasks).
CREATE TABLE IF NOT EXISTS simba.enrollment_tasks (
    id              BIGSERIAL PRIMARY KEY,
    enrollment_task_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    enrollment_id   UUID NOT NULL REFERENCES simba.plan_enrollments(enrollment_id),
    plan_task_id    UUID NOT NULL REFERENCES simba.plan_tasks(plan_task_id),
    title           VARCHAR(255) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING | DONE | SKIPPED
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_enroll_tasks ON simba.enrollment_tasks (enrollment_id, status);

-- ── Lightweight daily habit check-ins (distinct from quantitative goals) ──────────────
CREATE TABLE IF NOT EXISTS simba.habit_check_ins (
    id              BIGSERIAL PRIMARY KEY,
    check_in_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    habit_code      VARCHAR(64) NOT NULL,            -- e.g. MOVE_10MIN, WATER, MINDFUL, NO_TOBACCO
    goal_id         UUID,                            -- optional link to a simba.goals row
    enrollment_id   UUID,                            -- optional link to a plan enrollment
    status          VARCHAR(16) NOT NULL DEFAULT 'DONE',  -- DONE | MISSED
    check_in_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_habit_daily UNIQUE (tenant_id, person_cpid, habit_code, check_in_date)
);
CREATE INDEX IF NOT EXISTS idx_simba_habit_person ON simba.habit_check_ins (tenant_id, person_cpid, check_in_date DESC);

-- ── Coaching relationship (wellness coach ↔ participant). Coach identity stays Varapi/
--    Vashandi; this is a wellness relationship, NOT a clinical encounter. ──────────────
CREATE TABLE IF NOT EXISTS simba.coaching_relationships (
    id              BIGSERIAL PRIMARY KEY,
    relationship_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,           -- the participant
    coach_provider_id VARCHAR(128) NOT NULL,         -- Varapi provider id of the coach
    coach_cadre     VARCHAR(48),                     -- HEALTH_COACH | NUTRITIONIST | PHYSIO | COUNSELLOR | CHW
    focus           VARCHAR(64),                     -- ACTIVITY | NUTRITION | SLEEP | MENTAL_WELLBEING | CHRONIC_PREVENTION
    plan_id         UUID,                            -- optional linked wellness plan
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ENDED
    requested_by    VARCHAR(128),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_coaching UNIQUE (tenant_id, person_cpid, coach_provider_id, focus)
);
CREATE INDEX IF NOT EXISTS idx_simba_coaching_person ON simba.coaching_relationships (tenant_id, person_cpid, status);
CREATE INDEX IF NOT EXISTS idx_simba_coaching_coach ON simba.coaching_relationships (tenant_id, coach_provider_id, status);

CREATE TABLE IF NOT EXISTS simba.coaching_touchpoints (
    id              BIGSERIAL PRIMARY KEY,
    touchpoint_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    relationship_id UUID NOT NULL REFERENCES simba.coaching_relationships(relationship_id),
    touchpoint_type VARCHAR(32) NOT NULL DEFAULT 'CHECKIN',  -- CHECKIN | SESSION | NOTE | ESCALATION
    scheduled_at    TIMESTAMPTZ,
    note            TEXT,                            -- coaching note — NOT a clinical record unless routed to PCT
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_touchpoints_rel ON simba.coaching_touchpoints (relationship_id, created_at DESC);

-- ── General wellness access audit (cross-person, coaching, programme, care-routing) ───
CREATE TABLE IF NOT EXISTS simba.wellness_access_audit (
    id              BIGSERIAL PRIMARY KEY,
    audit_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    actor_id        VARCHAR(255),
    actor_type      VARCHAR(64),
    provider_id     VARCHAR(128),
    subject_cpid    VARCHAR(128),                    -- person the action targets (may differ from actor → cross-person)
    relationship    VARCHAR(64),                     -- SELF | DEPENDANT | COACH | PROGRAMME_ADMIN
    facility_id     VARCHAR(128),
    workspace_id    VARCHAR(128),
    action          VARCHAR(128) NOT NULL,
    decision        VARCHAR(16) NOT NULL DEFAULT 'ALLOW',  -- ALLOW | DENY
    reason          VARCHAR(255),
    correlation_id  VARCHAR(255),
    request_id      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_wellness_audit
    ON simba.wellness_access_audit (tenant_id, subject_cpid, created_at DESC);

-- ── Wellness-to-care linkage (a referral/screening request routed to PCT). Simba tracks
--    the linkage + status; the clinical workflow/results stay with PCT/Butano. ─────────
CREATE TABLE IF NOT EXISTS simba.care_linkages (
    id              BIGSERIAL PRIMARY KEY,
    linkage_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    trigger         VARCHAR(64) NOT NULL,            -- RED_FLAG | SCREENING_DUE | RISK_THRESHOLD | COACH_ESCALATION
    severity        VARCHAR(16) NOT NULL DEFAULT 'ROUTINE',  -- ROUTINE | URGENT | EMERGENCY
    reason          TEXT,
    target_owner    VARCHAR(32) NOT NULL DEFAULT 'PCT',      -- PCT | EMERGENCY | SCREENING
    external_ref    VARCHAR(128),                    -- e.g. PCT referral id, when created
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',     -- OPEN | ROUTED | CLOSED
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_care_linkage_person
    ON simba.care_linkages (tenant_id, person_cpid, status);

-- ── Governed national wellness plan seeds (compose / preview / e2e) ───────────────────
INSERT INTO simba.wellness_plans (
    plan_id, tenant_id, plan_code, name, description, category, audience, duration_days,
    fundo_content_ref, clinical_caution, created_by
) VALUES
(
    'cccccccc-0005-0001-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PLAN-WALK-30',
    '30-Day Walking Journey',
    'Build a daily walking habit over 30 days — start at 10 minutes, grow to 30.',
    'ACTIVITY', 'INDIVIDUAL', 30,
    'FUNDO-WELLNESS-ACTIVITY-101', FALSE, 'MOHCC-WELLNESS'
),
(
    'cccccccc-0005-0001-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PLAN-DM-PREVENT-8W',
    '8-Week Diabetes Prevention',
    'Lifestyle journey to reduce type-2 diabetes risk — nutrition, movement, and screening awareness.',
    'CHRONIC_PREVENTION', 'INDIVIDUAL', 56,
    'FUNDO-WELLNESS-CHRONIC-201', TRUE, 'MOHCC-WELLNESS'
),
(
    'cccccccc-0005-0001-0000-000000000003'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PLAN-SLEEP-14',
    '14-Day Better Sleep',
    'Sleep-hygiene journey — consistent bedtimes and wind-down routines.',
    'SLEEP', 'INDIVIDUAL', 14,
    'FUNDO-WELLNESS-SLEEP-101', FALSE, 'MOHCC-WELLNESS'
),
(
    'cccccccc-0005-0001-0000-000000000004'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'PLAN-TOBACCO-QUIT',
    'Tobacco Cessation Journey',
    'Step-down support to stop tobacco — coping habits and education, with care routing where needed.',
    'TOBACCO', 'INDIVIDUAL', 42,
    'FUNDO-WELLNESS-TOBACCO-101', TRUE, 'MOHCC-WELLNESS'
)
ON CONFLICT (tenant_id, plan_code) DO NOTHING;

INSERT INTO simba.plan_tasks (
    plan_task_id, tenant_id, plan_id, stage, seq, title, description, task_type, fundo_content_ref
) VALUES
(
    'dddddddd-0005-0001-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'cccccccc-0005-0001-0000-000000000001'::uuid,
    1, 1, 'Walk 10 minutes', 'A gentle 10-minute walk to start the journey.', 'HABIT', NULL
),
(
    'dddddddd-0005-0001-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'cccccccc-0005-0001-0000-000000000001'::uuid,
    1, 2, 'Learn: warm-up basics', 'Short Fundo lesson on safe warm-ups.', 'EDUCATION', 'FUNDO-WELLNESS-ACTIVITY-101'
),
(
    'dddddddd-0005-0001-0000-000000000003'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'cccccccc-0005-0001-0000-000000000001'::uuid,
    2, 1, 'Walk 20 minutes', 'Increase to 20 minutes as the habit settles.', 'HABIT', NULL
),
(
    'dddddddd-0005-0001-0000-000000000004'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'cccccccc-0005-0001-0000-000000000002'::uuid,
    1, 1, 'Know your risk', 'Reflect on diabetes risk factors and book screening if eligible.', 'SCREENING', 'FUNDO-WELLNESS-CHRONIC-201'
)
ON CONFLICT DO NOTHING;
