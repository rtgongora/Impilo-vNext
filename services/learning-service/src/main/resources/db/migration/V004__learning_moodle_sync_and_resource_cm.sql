-- Moodle activity ↔ catalog mapping + raw WS snapshots for audit/replay

ALTER TABLE lrn_learning_resource
    ADD COLUMN IF NOT EXISTS moodle_cm_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_lrn_resource_moodle_cm
    ON lrn_learning_resource (tenant_id, moodle_cm_id)
    WHERE moodle_cm_id IS NOT NULL AND active = TRUE;

CREATE TABLE lrn_moodle_ws_snapshot (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    ws_function         VARCHAR(160)    NOT NULL,
    course_id           VARCHAR(64),
    moodle_user_id      VARCHAR(64)     NOT NULL,
    subject_type        VARCHAR(32),
    subject_id          VARCHAR(255),
    correlation_id      VARCHAR(128),
    response_json       TEXT            NOT NULL,
    sync_summary_json   TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_lrn_moodle_snap_tenant ON lrn_moodle_ws_snapshot (tenant_id, created_at);
