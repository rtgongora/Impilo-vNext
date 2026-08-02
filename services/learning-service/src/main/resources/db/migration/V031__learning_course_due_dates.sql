-- Flexible course due dates.
--
-- Ported from impilo-learning-staging, where this was V015. That number is taken on
-- canonical by V015__learning_certificate_lifecycle, applied to preview on 2026-07-18,
-- so the original could never have been applied here — Flyway rejects a version already
-- present with different content. Renumbered onto canonical's sequence (head was V030);
-- content is otherwise unchanged.
--
-- Two modes: FIXED (an exact date shared by every enrolment) and RELATIVE (calculated
-- per learner from their enrolment date).

ALTER TABLE lrn_course
    ADD COLUMN due_date_type VARCHAR(32),
    ADD COLUMN due_date TIMESTAMP WITH TIME ZONE,
    ADD COLUMN due_date_days_from_enrollment INTEGER;

-- Constraint: due_date_type must be either FIXED or RELATIVE
ALTER TABLE lrn_course
    ADD CONSTRAINT chk_lrn_course_due_date_type
    CHECK (due_date_type IS NULL OR due_date_type IN ('FIXED', 'RELATIVE'));

-- Constraint: FIXED type must have dueDate set
ALTER TABLE lrn_course
    ADD CONSTRAINT chk_lrn_course_due_date_fixed
    CHECK (due_date_type != 'FIXED' OR due_date IS NOT NULL);

-- Constraint: RELATIVE type must have days_from_enrollment set
ALTER TABLE lrn_course
    ADD CONSTRAINT chk_lrn_course_due_date_relative
    CHECK (due_date_type != 'RELATIVE' OR due_date_days_from_enrollment IS NOT NULL);

COMMENT ON COLUMN lrn_course.due_date_type IS 'FIXED: exact due date | RELATIVE: calculated from enrollment date';
COMMENT ON COLUMN lrn_course.due_date IS 'For FIXED due_date_type: the exact due date for all enrollments';
COMMENT ON COLUMN lrn_course.due_date_days_from_enrollment IS 'For RELATIVE due_date_type: days after enrollment to calculate the due date';

-- Index for efficient queries of overdue courses
CREATE INDEX idx_lrn_course_due_date ON lrn_course(tenant_id, due_date)
    WHERE due_date IS NOT NULL AND status = 'PUBLISHED';
