-- Phase 5A — Native Impilo Fundo LMS foundation.
--
-- Adds eleven new tables under the existing `lrn_` prefix that together form
-- a native, modern, standalone-capable LMS schema: course, course module,
-- course lesson, pathway, pathway item, enrolment, course progress,
-- assessment, assessment question, assessment attempt, and certificate.
--
-- These tables are PURELY ADDITIVE. No existing table, column, index, or
-- constraint is modified. The legacy `lrn_learning_resource` /
-- `lrn_learning_path` schema continues to back the existing Moodle/Varapi
-- adapter flows and the legacy `/internal/v1/learning/**` endpoints.
-- Impilo Fundo's native LMS surface (catalog, structure, pathway, enrolment,
-- progress, assessment foundation, certificate metadata, learning record)
-- runs entirely on the tables below — no Moodle, no external LMS.

-- ── Course ────────────────────────────────────────────────────────────
CREATE TABLE lrn_course (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    code                            VARCHAR(160)    NOT NULL,
    title                           VARCHAR(512)    NOT NULL,
    description                     TEXT,
    category                        VARCHAR(128),
    level                           VARCHAR(64),
    status                          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    language                        VARCHAR(16)     NOT NULL DEFAULT 'en',
    estimated_duration_minutes      INT,
    mandatory                       BOOLEAN         NOT NULL DEFAULT FALSE,
    cpd_eligible                    BOOLEAN         NOT NULL DEFAULT FALSE,
    cpd_points                      INT,
    version                         INT             NOT NULL DEFAULT 1,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_course_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_lrn_course_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE INDEX idx_lrn_course_tenant_status ON lrn_course (tenant_id, status);
CREATE INDEX idx_lrn_course_category ON lrn_course (tenant_id, category);
CREATE INDEX idx_lrn_course_cpd ON lrn_course (tenant_id, cpd_eligible);

-- ── Course module ──────────────────────────────────────────────────────
CREATE TABLE lrn_course_module (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    title                           VARCHAR(512)    NOT NULL,
    description                     TEXT,
    sequence_no                     INT             NOT NULL,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_course_module_seq UNIQUE (course_id, sequence_no),
    CONSTRAINT chk_lrn_course_module_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE INDEX idx_lrn_course_module_course ON lrn_course_module (course_id);

-- ── Course lesson ──────────────────────────────────────────────────────
CREATE TABLE lrn_course_lesson (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    module_id                       UUID            NOT NULL REFERENCES lrn_course_module(id) ON DELETE CASCADE,
    title                           VARCHAR(512)    NOT NULL,
    content_type                    VARCHAR(32)     NOT NULL,
    content_body                    TEXT,
    content_ref                     VARCHAR(1024),
    estimated_duration_minutes      INT,
    sequence_no                     INT             NOT NULL,
    required                        BOOLEAN         NOT NULL DEFAULT TRUE,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_course_lesson_seq UNIQUE (module_id, sequence_no),
    CONSTRAINT chk_lrn_course_lesson_content_type CHECK (
        content_type IN ('TEXT','VIDEO','DOCUMENT','LINK','INTERACTIVE','PRACTICAL_TASK')
    ),
    CONSTRAINT chk_lrn_course_lesson_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE INDEX idx_lrn_course_lesson_module ON lrn_course_lesson (module_id);

-- ── Fundo pathway (course-level, distinct from legacy lrn_learning_path) ─
CREATE TABLE lrn_fundo_pathway (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    code                            VARCHAR(160)    NOT NULL,
    title                           VARCHAR(512)    NOT NULL,
    description                     TEXT,
    target_cadres                   TEXT,
    target_roles                    TEXT,
    target_facility_levels          TEXT,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_fundo_pathway_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_lrn_fundo_pathway_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE INDEX idx_lrn_fundo_pathway_tenant_status ON lrn_fundo_pathway (tenant_id, status);

CREATE TABLE lrn_fundo_pathway_item (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id                      UUID            NOT NULL REFERENCES lrn_fundo_pathway(id) ON DELETE CASCADE,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    sequence_no                     INT             NOT NULL,
    required                        BOOLEAN         NOT NULL DEFAULT TRUE,
    prerequisite_course_id          UUID            REFERENCES lrn_course(id) ON DELETE SET NULL,
    CONSTRAINT uq_lrn_fundo_pathway_item_seq UNIQUE (pathway_id, sequence_no)
);

CREATE INDEX idx_lrn_fundo_pathway_item_pathway ON lrn_fundo_pathway_item (pathway_id);

-- ── Enrolment ──────────────────────────────────────────────────────────
CREATE TABLE lrn_enrolment (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    subject_type                    VARCHAR(32)     NOT NULL,
    subject_id                      VARCHAR(255)    NOT NULL,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE RESTRICT,
    pathway_id                      UUID            REFERENCES lrn_fundo_pathway(id) ON DELETE SET NULL,
    enrolment_type                  VARCHAR(32)     NOT NULL DEFAULT 'SELF',
    status                          VARCHAR(32)     NOT NULL DEFAULT 'ENROLLED',
    assigned_by                     VARCHAR(255),
    assigned_at                     TIMESTAMPTZ,
    due_at                          TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_lrn_enrolment_subject_type CHECK (
        subject_type IN ('PROVIDER','STUDENT','STAFF','FACILITY_TEAM','OTHER')
    ),
    CONSTRAINT chk_lrn_enrolment_type CHECK (
        enrolment_type IN ('SELF','ASSIGNED','COHORT','SYSTEM')
    ),
    CONSTRAINT chk_lrn_enrolment_status CHECK (
        status IN ('ENROLLED','IN_PROGRESS','COMPLETED','CANCELLED','EXPIRED')
    )
);

-- Active enrolment uniqueness is enforced at the application layer (existing
-- ENROLLED/IN_PROGRESS row must be reused or cancelled before re-enrolment).
-- The partial unique index below names this rule for visibility; a runtime
-- pre-check in FundoEnrolmentService handles the idempotency contract on
-- the v1.1 POST endpoint. Two completed/cancelled rows on the same
-- (tenant, subject, course) are intentionally allowed (history).
CREATE UNIQUE INDEX uq_lrn_enrolment_active
    ON lrn_enrolment (tenant_id, subject_type, subject_id, course_id)
    WHERE status IN ('ENROLLED','IN_PROGRESS');

CREATE INDEX idx_lrn_enrolment_subject ON lrn_enrolment (tenant_id, subject_type, subject_id);
CREATE INDEX idx_lrn_enrolment_course ON lrn_enrolment (course_id);
CREATE INDEX idx_lrn_enrolment_pathway ON lrn_enrolment (pathway_id);

-- ── Course progress ────────────────────────────────────────────────────
-- Distinct from legacy lrn_learning_progress (which keys on resource_id).
-- This table tracks lesson/module/course-level native LMS progress against
-- a specific enrolment.
CREATE TABLE lrn_course_progress (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    enrolment_id                    UUID            NOT NULL REFERENCES lrn_enrolment(id) ON DELETE CASCADE,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE RESTRICT,
    module_id                       UUID            REFERENCES lrn_course_module(id) ON DELETE SET NULL,
    lesson_id                       UUID            REFERENCES lrn_course_lesson(id) ON DELETE SET NULL,
    subject_type                    VARCHAR(32)     NOT NULL,
    subject_id                      VARCHAR(255)    NOT NULL,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent                INT             NOT NULL DEFAULT 0,
    score                           INT,
    started_at                      TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,
    last_accessed_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_lrn_course_progress_status CHECK (
        status IN ('NOT_STARTED','STARTED','COMPLETED')
    ),
    CONSTRAINT chk_lrn_course_progress_percent CHECK (progress_percent BETWEEN 0 AND 100)
);

-- One row per (enrolment, lesson) — lesson-level granularity. Module/course
-- aggregate rows have lesson_id NULL; per-row uniqueness allows both
-- aggregate and lesson rows to coexist for the same enrolment.
CREATE UNIQUE INDEX uq_lrn_course_progress_lesson
    ON lrn_course_progress (enrolment_id, lesson_id)
    WHERE lesson_id IS NOT NULL;

CREATE UNIQUE INDEX uq_lrn_course_progress_module
    ON lrn_course_progress (enrolment_id, module_id)
    WHERE module_id IS NOT NULL AND lesson_id IS NULL;

CREATE UNIQUE INDEX uq_lrn_course_progress_course
    ON lrn_course_progress (enrolment_id)
    WHERE module_id IS NULL AND lesson_id IS NULL;

CREATE INDEX idx_lrn_course_progress_subject ON lrn_course_progress (tenant_id, subject_type, subject_id);
CREATE INDEX idx_lrn_course_progress_enrolment ON lrn_course_progress (enrolment_id);

-- ── Assessment foundation ──────────────────────────────────────────────
CREATE TABLE lrn_assessment (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE CASCADE,
    module_id                       UUID            REFERENCES lrn_course_module(id) ON DELETE SET NULL,
    title                           VARCHAR(512)    NOT NULL,
    description                     TEXT,
    assessment_type                 VARCHAR(32)     NOT NULL,
    pass_mark                       INT             NOT NULL DEFAULT 70,
    max_attempts                    INT             NOT NULL DEFAULT 3,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'PUBLISHED',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_lrn_assessment_type CHECK (
        assessment_type IN ('QUIZ','PRACTICAL_CHECKLIST','CASE_REVIEW')
    ),
    CONSTRAINT chk_lrn_assessment_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT chk_lrn_assessment_pass_mark CHECK (pass_mark BETWEEN 0 AND 100)
);

CREATE INDEX idx_lrn_assessment_course ON lrn_assessment (course_id);

CREATE TABLE lrn_assessment_question (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id                   UUID            NOT NULL REFERENCES lrn_assessment(id) ON DELETE CASCADE,
    question_type                   VARCHAR(32)     NOT NULL,
    prompt                          TEXT            NOT NULL,
    options_json                    TEXT,
    correct_answer_json             TEXT,
    sequence_no                     INT             NOT NULL,
    points                          INT             NOT NULL DEFAULT 1,
    CONSTRAINT uq_lrn_assessment_question_seq UNIQUE (assessment_id, sequence_no),
    CONSTRAINT chk_lrn_assessment_question_type CHECK (
        question_type IN ('MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','MATCHING','CASE_PROMPT')
    )
);

CREATE INDEX idx_lrn_assessment_question_assessment ON lrn_assessment_question (assessment_id);

CREATE TABLE lrn_assessment_attempt (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    assessment_id                   UUID            NOT NULL REFERENCES lrn_assessment(id) ON DELETE CASCADE,
    enrolment_id                    UUID            REFERENCES lrn_enrolment(id) ON DELETE SET NULL,
    subject_type                    VARCHAR(32)     NOT NULL,
    subject_id                      VARCHAR(255)    NOT NULL,
    attempt_no                      INT             NOT NULL,
    score                           INT,
    passed                          BOOLEAN,
    submitted_at                    TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_assessment_attempt_no UNIQUE (assessment_id, subject_type, subject_id, attempt_no)
);

CREATE INDEX idx_lrn_assessment_attempt_subject ON lrn_assessment_attempt (tenant_id, subject_type, subject_id);

-- ── Certificate metadata (ordinary native — NOT signed credentials) ────
CREATE TABLE lrn_certificate (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            NOT NULL,
    enrolment_id                    UUID            NOT NULL REFERENCES lrn_enrolment(id) ON DELETE RESTRICT,
    course_id                       UUID            NOT NULL REFERENCES lrn_course(id) ON DELETE RESTRICT,
    subject_type                    VARCHAR(32)     NOT NULL,
    subject_id                      VARCHAR(255)    NOT NULL,
    certificate_number              VARCHAR(64)     NOT NULL,
    title                           VARCHAR(512)    NOT NULL,
    issued_at                       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    valid_until                     TIMESTAMPTZ,
    status                          VARCHAR(32)     NOT NULL DEFAULT 'ISSUED',
    cpd_eligible                    BOOLEAN         NOT NULL DEFAULT FALSE,
    cpd_points                      INT,
    CONSTRAINT uq_lrn_certificate_number UNIQUE (tenant_id, certificate_number),
    CONSTRAINT uq_lrn_certificate_enrolment UNIQUE (enrolment_id),
    CONSTRAINT chk_lrn_certificate_status CHECK (status IN ('ISSUED','REVOKED','EXPIRED'))
);

CREATE INDEX idx_lrn_certificate_subject ON lrn_certificate (tenant_id, subject_type, subject_id);
