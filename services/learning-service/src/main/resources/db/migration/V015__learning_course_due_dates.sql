-- Add flexible due date support to courses (Phase 5B)
-- Supports two modes: FIXED (exact date) and RELATIVE (days from enrollment)
-- This enables courses to have adaptive due dates calculated from enrollment date

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

-- Comment for clarity
COMMENT ON COLUMN lrn_course.due_date_type IS 'FIXED: exact due date | RELATIVE: calculated from enrollment date';
COMMENT ON COLUMN lrn_course.due_date IS 'For FIXED due_date_type: the exact due date for all enrollments';
COMMENT ON COLUMN lrn_course.due_date_days_from_enrollment IS 'For RELATIVE due_date_type: days after enrollment to calculate the due date';

-- Create index on due_date for efficient queries of overdue courses
CREATE INDEX idx_lrn_course_due_date ON lrn_course(tenant_id, due_date)
    WHERE due_date IS NOT NULL AND status = 'PUBLISHED';
