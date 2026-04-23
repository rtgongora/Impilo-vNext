-- Learning capability layer — catalog, paths, workflow/helpdesk/CPD linkages, learner state, audit, outbox

CREATE TABLE lrn_learning_resource (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    resource_code       VARCHAR(160)    NOT NULL,
    source_type         VARCHAR(32)     NOT NULL,
    title               VARCHAR(512)    NOT NULL,
    description         TEXT,
    resource_type       VARCHAR(64)     NOT NULL,
    external_ref        VARCHAR(512),
    app_code            VARCHAR(64),
    role_tags           TEXT,
    workflow_tags       TEXT,
    lifecycle_tags      TEXT,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_resource_code UNIQUE (tenant_id, resource_code)
);

CREATE INDEX idx_lrn_resource_tenant ON lrn_learning_resource (tenant_id);
CREATE INDEX idx_lrn_resource_active ON lrn_learning_resource (tenant_id, active);

CREATE TABLE lrn_learning_path (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    path_code           VARCHAR(160)    NOT NULL,
    title               VARCHAR(512)    NOT NULL,
    description         TEXT,
    path_type           VARCHAR(64)     NOT NULL DEFAULT 'ROLE',
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_path_code UNIQUE (tenant_id, path_code)
);

CREATE TABLE lrn_learning_path_item (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    path_id             UUID            NOT NULL REFERENCES lrn_learning_path(id) ON DELETE CASCADE,
    resource_id         UUID            NOT NULL REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    sequence_no         INT             NOT NULL,
    CONSTRAINT uq_lrn_path_seq UNIQUE (path_id, sequence_no)
);

CREATE TABLE lrn_role_learning_requirement (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    role_code           VARCHAR(128)    NOT NULL,
    resource_id         UUID            REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    path_id             UUID            REFERENCES lrn_learning_path(id) ON DELETE CASCADE,
    requirement_type    VARCHAR(32)     NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json       TEXT,
    CONSTRAINT chk_lrn_rr_target CHECK (resource_id IS NOT NULL OR path_id IS NOT NULL)
);

CREATE INDEX idx_lrn_rr_role ON lrn_role_learning_requirement (tenant_id, role_code, active);

CREATE TABLE lrn_workflow_learning_link (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    app_code                VARCHAR(64)     NOT NULL,
    workflow_code           VARCHAR(128),
    screen_or_route_ref     VARCHAR(512),
    resource_id             UUID            REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    path_id                 UUID            REFERENCES lrn_learning_path(id) ON DELETE CASCADE,
    link_type               VARCHAR(64)     NOT NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json           TEXT,
    CONSTRAINT chk_lrn_wl_target CHECK (resource_id IS NOT NULL OR path_id IS NOT NULL)
);

CREATE INDEX idx_lrn_wl_lookup ON lrn_workflow_learning_link (tenant_id, app_code, active);

CREATE TABLE lrn_user_learning_profile (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    subject_type        VARCHAR(32)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    fundo_user_ref      VARCHAR(255),
    profile_status      VARCHAR(64)     NOT NULL DEFAULT 'ACTIVE',
    metadata_json       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_profile UNIQUE (tenant_id, subject_type, subject_id)
);

CREATE TABLE lrn_learning_progress (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    subject_type        VARCHAR(32)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    resource_id         UUID            NOT NULL REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    status              VARCHAR(32)     NOT NULL,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    source_ref          VARCHAR(512),
    metadata_json       TEXT,
    CONSTRAINT uq_lrn_progress UNIQUE (tenant_id, subject_type, subject_id, resource_id)
);

CREATE INDEX idx_lrn_progress_subject ON lrn_learning_progress (tenant_id, subject_type, subject_id);

CREATE TABLE lrn_learning_completion (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    subject_type        VARCHAR(32)     NOT NULL,
    subject_id          VARCHAR(255)    NOT NULL,
    resource_id         UUID            NOT NULL REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    completion_date     TIMESTAMPTZ     NOT NULL,
    source_type         VARCHAR(32)     NOT NULL,
    source_ref          VARCHAR(512)    NOT NULL,
    verified_state      VARCHAR(32)     NOT NULL DEFAULT 'UNVERIFIED',
    metadata_json       TEXT
);

CREATE INDEX idx_lrn_completion_subject ON lrn_learning_completion (tenant_id, subject_type, subject_id);

CREATE TABLE lrn_helpdesk_knowledge_link (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    issue_type_or_topic     VARCHAR(128)    NOT NULL,
    resource_id             UUID            REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    path_id                 UUID            REFERENCES lrn_learning_path(id) ON DELETE CASCADE,
    relation_type           VARCHAR(64)     NOT NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json           TEXT,
    CONSTRAINT chk_lrn_hd_target CHECK (resource_id IS NOT NULL OR path_id IS NOT NULL)
);

CREATE INDEX idx_lrn_hd_issue ON lrn_helpdesk_knowledge_link (tenant_id, issue_type_or_topic, active);

CREATE TABLE lrn_cpd_learning_link (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    resource_id             UUID            NOT NULL REFERENCES lrn_learning_resource(id) ON DELETE CASCADE,
    council_ref             BIGINT,
    cpd_eligible            BOOLEAN         NOT NULL DEFAULT FALSE,
    default_credit_value    INT             NOT NULL DEFAULT 0,
    requires_review_flag    BOOLEAN         NOT NULL DEFAULT TRUE,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json           TEXT
);

CREATE TABLE lrn_contextual_learning_hint (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    app_code                VARCHAR(64)     NOT NULL,
    route_or_component_ref  VARCHAR(512)    NOT NULL,
    hint_type               VARCHAR(64)     NOT NULL,
    text_summary            VARCHAR(2000)   NOT NULL,
    resource_id             UUID            REFERENCES lrn_learning_resource(id) ON DELETE SET NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    metadata_json           TEXT
);

CREATE INDEX idx_lrn_hint_lookup ON lrn_contextual_learning_hint (tenant_id, app_code, active);

CREATE TABLE lrn_learning_audit_event (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    actor_id                VARCHAR(255),
    actor_type              VARCHAR(64),
    action                  VARCHAR(128)    NOT NULL,
    target_type             VARCHAR(64),
    target_id               VARCHAR(255),
    outcome                 VARCHAR(64),
    correlation_id          VARCHAR(128),
    workflow_context_json   TEXT,
    metadata_json           TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_lrn_audit_tenant ON lrn_learning_audit_event (tenant_id, created_at);

CREATE TABLE lrn_event_outbox (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(256)    NOT NULL,
    payload_json    TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_lrn_outbox_published_at ON lrn_event_outbox (published_at);
