-- Phase: LMS production-grade hardening
-- Adds manual-review/rubric support and richer lesson authoring storage.

ALTER TABLE lrn_course_lesson
    ADD COLUMN IF NOT EXISTS content_format VARCHAR(32) NOT NULL DEFAULT 'PLAIN_TEXT',
    ADD COLUMN IF NOT EXISTS content_blocks_json TEXT;

ALTER TABLE lrn_assessment_question
    ADD COLUMN IF NOT EXISTS rubric_json TEXT;

ALTER TABLE lrn_assessment_attempt
    ADD COLUMN IF NOT EXISTS manual_review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_AUTO',
    ADD COLUMN IF NOT EXISTS manual_review_score INTEGER,
    ADD COLUMN IF NOT EXISTS manual_review_passed BOOLEAN,
    ADD COLUMN IF NOT EXISTS reviewer_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rubric_applied_json TEXT,
    ADD COLUMN IF NOT EXISTS feedback_text TEXT;

CREATE INDEX IF NOT EXISTS ix_lrn_assessment_attempt_review_status
    ON lrn_assessment_attempt (tenant_id, assessment_id, manual_review_status);
