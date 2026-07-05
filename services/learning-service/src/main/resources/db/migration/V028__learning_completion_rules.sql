-- V028: Configurable course completion rules (LEARNING_LIVE W3).
--
-- lrn_completion_rule is the completionCriteriaRef target named by the
-- libs/session-templates learning-live.json template. A course with NO rules
-- keeps the legacy implicit behaviour (all required lessons at 100 %); a course
-- WITH rules only transitions enrolments to COMPLETED when every required rule
-- is satisfied (AND semantics) — evaluated by FundoCompletionPolicyService.
--
-- PK convention: like every other lrn_* table (V006/V017/V018 …) the id has no
-- database default; the JPA entity generates the UUID in @PrePersist.
--
-- rule_type semantics:
--   ATTENDANCE_THRESHOLD — threshold_value = minimum total attended MINUTES
--                          across the course's LIVE sessions (lrn_session_attendance
--                          .watch_minutes); NULL threshold = any PRESENT attendance.
--   QUIZ_REQUIRED        — at least one passed attempt on a QUIZ assessment.
--   ASSESSMENT_PASS      — a passed FINAL_ASSESSMENT / POST_TEST attempt.
--   FACILITATOR_CONFIRM  — lrn_enrolment.facilitator_confirmed_at is set.
--   LESSON_PROGRESS      — legacy all-required-lessons-at-100 rule, explicit form.
--   WATCH_THRESHOLD      — reserved for W4 replay/watch progress (evaluates false).

CREATE TABLE IF NOT EXISTS lrn_completion_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    rule_type VARCHAR(32) NOT NULL
        CONSTRAINT ck_lrn_completion_rule_type
        CHECK (rule_type IN (
            'ATTENDANCE_THRESHOLD', 'QUIZ_REQUIRED', 'ASSESSMENT_PASS',
            'FACILITATOR_CONFIRM', 'LESSON_PROGRESS', 'WATCH_THRESHOLD')),
    threshold_value NUMERIC NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(128) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_completion_rule UNIQUE (tenant_id, course_id, rule_type)
);

CREATE INDEX IF NOT EXISTS idx_lrn_completion_rule_course
    ON lrn_completion_rule (course_id);

-- Facilitator confirmation marker consumed by the FACILITATOR_CONFIRM rule.
-- Written by POST /internal/v1/learning/v11/enrolments/{id}/facilitator-confirm.
ALTER TABLE lrn_enrolment
    ADD COLUMN IF NOT EXISTS facilitator_confirmed_at TIMESTAMPTZ NULL;

ALTER TABLE lrn_enrolment
    ADD COLUMN IF NOT EXISTS facilitator_confirmed_by VARCHAR(255) NULL;
