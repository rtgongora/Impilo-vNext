-- V022: Learning-space scope (the delegated-admin spine).
--
-- A learning_space_id (the workforce-governance OrganisationUnit id of an academy/
-- school/training-centre) optionally scopes ownable content/learners to a provider's
-- space. NULL = national/tenant-wide content (back-compat). Additive + nullable.

ALTER TABLE lrn_course             ADD COLUMN IF NOT EXISTS learning_space_id UUID NULL;
ALTER TABLE lrn_learning_cohort    ADD COLUMN IF NOT EXISTS learning_space_id UUID NULL;
ALTER TABLE lrn_academic_program   ADD COLUMN IF NOT EXISTS learning_space_id UUID NULL;
ALTER TABLE lrn_enrolment          ADD COLUMN IF NOT EXISTS learning_space_id UUID NULL;

CREATE INDEX IF NOT EXISTS idx_lrn_course_space ON lrn_course (tenant_id, learning_space_id);
CREATE INDEX IF NOT EXISTS idx_lrn_academic_program_space ON lrn_academic_program (tenant_id, learning_space_id);
