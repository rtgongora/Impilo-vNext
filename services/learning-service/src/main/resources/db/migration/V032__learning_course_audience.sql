-- Course targeting for Fundo recommendations.
--
-- Ported from impilo-learning-staging, where this was V016 — taken on canonical by
-- V016__learning_facilitator_venue (applied 2026-07-18). Renumbered; content unchanged.
--
-- Exactly one audience mode is active for each course. Role selections are only valid
-- when audience_type = 'SPECIFIC_ROLES'.
--
-- The NOT NULL DEFAULT is safe on a populated table: existing courses adopt
-- 'ALL_LEARNERS', which is the pre-migration behaviour (no targeting = everyone).

ALTER TABLE lrn_course
    ADD COLUMN IF NOT EXISTS audience_type VARCHAR(32) NOT NULL DEFAULT 'ALL_LEARNERS',
    ADD COLUMN IF NOT EXISTS audience_roles TEXT;

ALTER TABLE lrn_course
    ADD CONSTRAINT chk_lrn_course_audience_type
    CHECK (audience_type IN ('ALL_LEARNERS','SYSTEM_USERS','CITIZENS','SPECIFIC_ROLES'));

ALTER TABLE lrn_course
    ADD CONSTRAINT chk_lrn_course_audience_roles
    CHECK (
        (audience_type = 'SPECIFIC_ROLES' AND audience_roles IS NOT NULL AND btrim(audience_roles) <> '')
        OR (audience_type <> 'SPECIFIC_ROLES' AND audience_roles IS NULL)
    );

CREATE INDEX IF NOT EXISTS idx_lrn_course_audience
    ON lrn_course (tenant_id, status, audience_type);
