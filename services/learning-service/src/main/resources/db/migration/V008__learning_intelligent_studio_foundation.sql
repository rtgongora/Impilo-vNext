-- Phase 8: Intelligent Fundo Studio foundation (native, standalone).
-- Additive schema only. No external LMS dependency.

ALTER TABLE IF EXISTS lrn_course_lesson
    ADD COLUMN IF NOT EXISTS content_blocks_json TEXT,
    ADD COLUMN IF NOT EXISTS learner_preview_json TEXT,
    ADD COLUMN IF NOT EXISTS creator_notes_json TEXT;

ALTER TABLE IF EXISTS lrn_assessment
    DROP CONSTRAINT IF EXISTS chk_lrn_assessment_type;

ALTER TABLE IF EXISTS lrn_assessment
    ADD CONSTRAINT chk_lrn_assessment_type CHECK (
        assessment_type IN (
            'PRE_TEST',
            'POST_TEST',
            'QUIZ',
            'FINAL_ASSESSMENT',
            'SURVEY',
            'FEEDBACK',
            'PRACTICAL_CHECKLIST',
            'CASE_REVIEW'
        )
    );

CREATE TABLE IF NOT EXISTS lrn_library_resource (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    title VARCHAR(512) NOT NULL,
    description TEXT NULL,
    resource_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(512) NULL,
    mime_type VARCHAR(255) NULL,
    storage_ref VARCHAR(1024) NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    owner VARCHAR(255) NULL,
    language VARCHAR(16) NULL,
    tags_json TEXT NULL,
    metadata_json TEXT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    source_attribution TEXT NULL,
    archived_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_library_resource_tenant
    ON lrn_library_resource (tenant_id, review_status);

CREATE TABLE IF NOT EXISTS lrn_library_resource_version (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL REFERENCES lrn_library_resource(id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    storage_ref VARCHAR(1024) NULL,
    metadata_json TEXT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_library_resource_version UNIQUE (resource_id, version_no)
);

CREATE TABLE IF NOT EXISTS lrn_library_usage_link (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    resource_id UUID NOT NULL REFERENCES lrn_library_resource(id) ON DELETE CASCADE,
    link_type VARCHAR(64) NOT NULL,
    linked_entity_id VARCHAR(255) NOT NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_library_usage_link_resource
    ON lrn_library_usage_link (resource_id, link_type);

CREATE TABLE IF NOT EXISTS lrn_media_asset (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    title VARCHAR(512) NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    file_name VARCHAR(512) NULL,
    mime_type VARCHAR(255) NULL,
    storage_ref VARCHAR(1024) NULL,
    transcript TEXT NULL,
    caption_ref VARCHAR(1024) NULL,
    voiceover_script TEXT NULL,
    recording_type VARCHAR(64) NULL,
    source_lesson_id UUID NULL,
    duration_seconds INT NULL,
    metadata_json TEXT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_media_asset_tenant_status
    ON lrn_media_asset (tenant_id, status);

CREATE TABLE IF NOT EXISTS lrn_ai_generation_draft (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    generation_type VARCHAR(64) NOT NULL,
    target_course_id UUID NULL,
    created_by VARCHAR(255) NOT NULL,
    created_by_ai BOOLEAN NOT NULL DEFAULT TRUE,
    model_provider VARCHAR(128) NOT NULL,
    prompt TEXT NOT NULL,
    source_documents_json TEXT NULL,
    request_json TEXT NULL,
    output_json TEXT NOT NULL,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    human_reviewer VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_ai_generation_draft_tenant
    ON lrn_ai_generation_draft (tenant_id, review_status, created_at DESC);

CREATE TABLE IF NOT EXISTS lrn_interactive_activity (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NULL,
    lesson_id UUID NULL,
    activity_type VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    config_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS lrn_interactive_response (
    id UUID PRIMARY KEY,
    activity_id UUID NOT NULL REFERENCES lrn_interactive_activity(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_interactive_response_activity
    ON lrn_interactive_response (activity_id, created_at DESC);

CREATE TABLE IF NOT EXISTS lrn_learning_notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    event_code VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    message TEXT NOT NULL,
    channel_preference VARCHAR(64) NULL,
    scheduled_at TIMESTAMPTZ NULL,
    due_at TIMESTAMPTZ NULL,
    reminder_at TIMESTAMPTZ NULL,
    recurrence VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    metadata_json TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_lrn_learning_notification_subject
    ON lrn_learning_notification (tenant_id, subject_type, subject_id, created_at DESC);

CREATE TABLE IF NOT EXISTS lrn_course_availability (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    availability_type VARCHAR(32) NOT NULL DEFAULT 'SELF_PACED',
    available_from TIMESTAMPTZ NULL,
    available_until TIMESTAMPTZ NULL,
    due_days INT NULL,
    refresher_every_days INT NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS lrn_learning_cohort (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    code VARCHAR(160) NOT NULL,
    title VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    starts_at TIMESTAMPTZ NULL,
    ends_at TIMESTAMPTZ NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_learning_cohort_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS lrn_cohort_membership (
    id UUID PRIMARY KEY,
    cohort_id UUID NOT NULL REFERENCES lrn_learning_cohort(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_lrn_cohort_membership UNIQUE (cohort_id, subject_type, subject_id)
);

CREATE TABLE IF NOT EXISTS lrn_scheduled_learning_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    cohort_id UUID NULL REFERENCES lrn_learning_cohort(id) ON DELETE SET NULL,
    title VARCHAR(512) NOT NULL,
    session_type VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NULL,
    facilitator VARCHAR(255) NULL,
    location_ref VARCHAR(1024) NULL,
    metadata_json TEXT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
