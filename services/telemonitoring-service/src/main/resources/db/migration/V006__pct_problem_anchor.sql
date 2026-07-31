-- Wave 5.2: problem-list anchor from tm_monitoring_plans to pct_problems (contribute, not copy).
-- pct_problems is the SoR; pct_problem_ref is the contribution's reference back when it succeeds.

ALTER TABLE telemonitoring.tm_monitoring_plans
    ADD COLUMN IF NOT EXISTS pct_problem_ref UUID;

ALTER TABLE telemonitoring.tm_monitoring_plans
    ADD COLUMN IF NOT EXISTS pct_problem_contributed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_tm_plans_uncontributed
    ON telemonitoring.tm_monitoring_plans (id)
    WHERE pct_problem_ref IS NULL AND status = 'ACTIVE';
