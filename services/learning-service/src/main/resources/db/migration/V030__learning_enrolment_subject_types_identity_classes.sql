-- V030 — admit the experience-layer identity classes as enrolment subjects.
--
-- The V006 check constraint predates the identity rearchitecture: the shell
-- and the v11 native service both address learners as USER_HEALTH_ID (citizen
-- Health-ID anchor; the LearningV11NativeService default) or PROVIDER_PUBLIC_ID
-- (Varapi public provider id), but the constraint only admitted the legacy
-- cohort classes — so every browser-originated self-enrolment failed on insert.
-- Legacy values stay valid; no rows need rewriting.

ALTER TABLE lrn_enrolment
    DROP CONSTRAINT IF EXISTS chk_lrn_enrolment_subject_type;

ALTER TABLE lrn_enrolment
    ADD CONSTRAINT chk_lrn_enrolment_subject_type CHECK (
        subject_type IN (
            'PROVIDER',
            'STUDENT',
            'STAFF',
            'FACILITY_TEAM',
            'OTHER',
            'USER_HEALTH_ID',
            'PROVIDER_PUBLIC_ID'
        )
    );
