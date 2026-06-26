-- V018: Assignments / tasks + submissions + marking (distinct from quiz assessments).
--
-- Assessments (lrn_assessment) are auto-scored quizzes. Assignments are
-- facilitator-set tasks with free deliverables and HUMAN marking. The marking
-- block mirrors the AssessmentAttempt manual-review columns so the marking
-- workflow/queue is identical in shape.

CREATE TABLE IF NOT EXISTS lrn_assignment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    cohort_id UUID NULL REFERENCES lrn_learning_cohort(id) ON DELETE SET NULL,
    module_id UUID NULL REFERENCES lrn_course_module(id) ON DELETE SET NULL,
    title VARCHAR(512) NOT NULL,
    instructions TEXT NULL,
    max_score INT NULL,
    rubric_json TEXT NULL,
    due_at TIMESTAMPTZ NULL,
    allow_late BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lrn_assignment_course ON lrn_assignment (tenant_id, course_id);
CREATE INDEX IF NOT EXISTS idx_lrn_assignment_cohort ON lrn_assignment (tenant_id, cohort_id);

CREATE TABLE IF NOT EXISTS lrn_assignment_submission (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    assignment_id UUID NOT NULL REFERENCES lrn_assignment(id) ON DELETE CASCADE,
    enrolment_id UUID NULL REFERENCES lrn_enrolment(id) ON DELETE SET NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    submission_no INT NOT NULL DEFAULT 1,
    submitted_at TIMESTAMPTZ NULL,
    body_text TEXT NULL,
    media_ref_json TEXT NULL,
    marking_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_MARK',
    score INT NULL,
    passed BOOLEAN NULL,
    marker_id VARCHAR(255) NULL,
    marked_at TIMESTAMPTZ NULL,
    rubric_applied_json TEXT NULL,
    feedback_text TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_assignment_submission UNIQUE (assignment_id, subject_type, subject_id, submission_no)
);
CREATE INDEX IF NOT EXISTS idx_lrn_assignment_submission_queue
    ON lrn_assignment_submission (tenant_id, marking_status);
