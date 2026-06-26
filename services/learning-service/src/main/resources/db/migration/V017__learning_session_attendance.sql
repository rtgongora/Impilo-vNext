-- V017: Session attendance + optional code/QR check-in.
--
-- Attendance uses the same subject_type/subject_id idiom as cohort membership and
-- enrolment, and optionally links to a course enrolment so a check-in can later
-- inform enrolment progress. Check-in tokens support self check-in (code/QR).

CREATE TABLE IF NOT EXISTS lrn_session_attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES lrn_scheduled_learning_session(id) ON DELETE CASCADE,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    enrolment_id UUID NULL REFERENCES lrn_enrolment(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PRESENT',
    check_in_at TIMESTAMPTZ NULL,
    check_in_method VARCHAR(32) NULL,
    recorded_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_session_attendance UNIQUE (session_id, subject_type, subject_id)
);
CREATE INDEX IF NOT EXISTS idx_lrn_session_attendance_session
    ON lrn_session_attendance (tenant_id, session_id);

CREATE TABLE IF NOT EXISTS lrn_session_checkin_token (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES lrn_scheduled_learning_session(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    single_use BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMPTZ NULL,
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_session_checkin_token UNIQUE (tenant_id, token)
);
