-- ============================================================================
-- workflow-service V002 — Workflow definitions, instances, and tasks
-- ============================================================================

-- Versioned workflow definitions (BPMN/DMN-like)
CREATE TABLE wf_definitions (
    definition_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(128)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    category        VARCHAR(64)     DEFAULT 'GENERAL',
    version         INT             NOT NULL DEFAULT 1,
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
    schema_json     JSONB,
    steps_json      JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_wf_def_tenant_code_version UNIQUE (tenant_id, code, version)
);

CREATE INDEX idx_wf_definitions_tenant ON wf_definitions (tenant_id, status);

-- Workflow instances (running executions)
CREATE TABLE wf_instances (
    instance_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id   UUID            NOT NULL REFERENCES wf_definitions(definition_id),
    tenant_id       UUID            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'CREATED',
    current_step    VARCHAR(128),
    context_json    JSONB           DEFAULT '{}',
    initiator_ref   VARCHAR(255)    NOT NULL,
    subject_ref     VARCHAR(255),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wf_instances_tenant ON wf_instances (tenant_id, status);
CREATE INDEX idx_wf_instances_def ON wf_instances (definition_id);

-- Task/step transitions within an instance
CREATE TABLE wf_tasks (
    task_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id     UUID            NOT NULL REFERENCES wf_instances(instance_id),
    tenant_id       UUID            NOT NULL,
    step_name       VARCHAR(128)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    assignee_ref    VARCHAR(255),
    input_json      JSONB,
    output_json     JSONB,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wf_tasks_instance ON wf_tasks (instance_id);
CREATE INDEX idx_wf_tasks_tenant ON wf_tasks (tenant_id, status);
