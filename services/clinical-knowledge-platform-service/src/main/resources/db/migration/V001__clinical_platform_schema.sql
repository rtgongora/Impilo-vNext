-- =============================================================================
-- Clinical Knowledge Platform — core schema (EDLIZ / national guidance)
-- Bounded contexts: source, model, rules, pathways, audit (same DB; split-ready)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS clinical;

-- ---------------------------------------------------------------------------
-- Source governance
-- ---------------------------------------------------------------------------
CREATE TABLE clinical.source_documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                   VARCHAR(512)    NOT NULL,
    source_type             VARCHAR(64)     NOT NULL,
    issuing_authority       VARCHAR(255)    NOT NULL DEFAULT 'MoHCC',
    effective_date          DATE            NOT NULL,
    publication_date        DATE,
    version_label           VARCHAR(64)     NOT NULL,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    supersedes_document_id  UUID REFERENCES clinical.source_documents(id),
    language                VARCHAR(16)     NOT NULL DEFAULT 'en',
    file_reference          VARCHAR(1024),
    content_hash            VARCHAR(128),
    ingestion_status        VARCHAR(32)     NOT NULL DEFAULT 'INDEXED',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sd_status_effective ON clinical.source_documents (status, effective_date DESC);

CREATE TABLE clinical.source_sections (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID            NOT NULL REFERENCES clinical.source_documents(id) ON DELETE CASCADE,
    page_number         INTEGER,
    section_title       VARCHAR(512),
    section_path        VARCHAR(1024),
    raw_text            TEXT            NOT NULL,
    normalized_text     TEXT,
    chunk_embedding_ref VARCHAR(255),
    start_offset        INTEGER,
    end_offset          INTEGER,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ss_document ON clinical.source_sections (document_id);
CREATE INDEX idx_ss_page ON clinical.source_sections (document_id, page_number);

-- ---------------------------------------------------------------------------
-- Structured knowledge
-- ---------------------------------------------------------------------------
CREATE TABLE clinical.clinical_knowledge_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                VARCHAR(64)     NOT NULL,
    canonical_code      VARCHAR(128),
    title               VARCHAR(512)    NOT NULL,
    summary             TEXT,
    population          VARCHAR(255),
    eligibility_json  JSONB           NOT NULL DEFAULT '{}'::jsonb,
    logic_json          JSONB           NOT NULL DEFAULT '{}'::jsonb,
    evidence_json       JSONB           NOT NULL DEFAULT '[]'::jsonb,
    source_refs_json    JSONB           NOT NULL DEFAULT '[]'::jsonb,
    effective_start     DATE            NOT NULL,
    effective_end       DATE,
    approval_status     VARCHAR(32)     NOT NULL DEFAULT 'APPROVED',
    version             INTEGER         NOT NULL DEFAULT 1,
    created_by          VARCHAR(255),
    approved_by         VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cki_type ON clinical.clinical_knowledge_items (type, approval_status);
CREATE INDEX idx_cki_effective ON clinical.clinical_knowledge_items (effective_start, effective_end);

CREATE TABLE clinical.medicine_guidance (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medicine_name           VARCHAR(255)    NOT NULL,
    generic_name            VARCHAR(255),
    route                   VARCHAR(64),
    dose_expression         VARCHAR(255),
    dose_unit               VARCHAR(64),
    frequency_expression    VARCHAR(255),
    duration_expression     VARCHAR(255),
    level_of_care           VARCHAR(8),
    ven_class               VARCHAR(8),
    indication              VARCHAR(512),
    alternative_rank        INTEGER         NOT NULL DEFAULT 1,
    specialist_only         BOOLEAN         NOT NULL DEFAULT FALSE,
    notes                   TEXT,
    contraindications_json  JSONB           NOT NULL DEFAULT '[]'::jsonb,
    monitoring_json         JSONB           NOT NULL DEFAULT '[]'::jsonb,
    source_section_id       UUID REFERENCES clinical.source_sections(id),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mg_generic ON clinical.medicine_guidance (generic_name);
CREATE INDEX idx_mg_loc ON clinical.medicine_guidance (level_of_care);

CREATE TABLE clinical.condition_guidance (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    condition_name              VARCHAR(255)    NOT NULL,
    severity                    VARCHAR(64),
    age_group                   VARCHAR(64),
    sex_constraints             VARCHAR(64),
    pregnancy_constraints       VARCHAR(255),
    first_line_guidance_id      UUID REFERENCES clinical.medicine_guidance(id),
    alternative_guidance_ids    JSONB           NOT NULL DEFAULT '[]'::jsonb,
    investigations_json         JSONB           NOT NULL DEFAULT '[]'::jsonb,
    red_flags_json              JSONB           NOT NULL DEFAULT '[]'::jsonb,
    counselling_json            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    followup_json               JSONB           NOT NULL DEFAULT '[]'::jsonb,
    source_section_id           UUID REFERENCES clinical.source_sections(id),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cg_condition ON clinical.condition_guidance (condition_name);

CREATE TABLE clinical.rule_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(128)    NOT NULL UNIQUE,
    name                    VARCHAR(255)    NOT NULL,
    rule_type               VARCHAR(64)     NOT NULL,
    trigger_context         VARCHAR(128),
    input_schema            JSONB           NOT NULL DEFAULT '{}'::jsonb,
    logic_expression        TEXT,
    severity                VARCHAR(32)     NOT NULL,
    message_template        TEXT            NOT NULL,
    explanation_template    TEXT,
    interruptive            BOOLEAN         NOT NULL DEFAULT FALSE,
    override_allowed        BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_start         DATE            NOT NULL,
    effective_end           DATE,
    version                 INTEGER         NOT NULL DEFAULT 1,
    source_refs_json        JSONB           NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rd_type ON clinical.rule_definitions (rule_type, effective_start);

CREATE TABLE clinical.pathway_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_name    VARCHAR(255)    NOT NULL,
    condition_code  VARCHAR(128)    NOT NULL,
    version         INTEGER         NOT NULL DEFAULT 1,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    source_section_id UUID REFERENCES clinical.source_sections(id),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pd_condition ON clinical.pathway_definitions (condition_code, status);

CREATE TABLE clinical.pathway_steps (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id              UUID            NOT NULL REFERENCES clinical.pathway_definitions(id) ON DELETE CASCADE,
    step_order                INTEGER         NOT NULL,
    step_type                 VARCHAR(64)     NOT NULL,
    prompt                    TEXT            NOT NULL,
    data_capture_schema       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    branch_logic              JSONB,
    recommendation_logic      JSONB,
    source_section_id         UUID REFERENCES clinical.source_sections(id),
    UNIQUE (pathway_id, step_order)
);

CREATE INDEX idx_ps_pathway ON clinical.pathway_steps (pathway_id);

CREATE TABLE clinical.pathway_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id          UUID            NOT NULL REFERENCES clinical.pathway_definitions(id),
    tenant_id           VARCHAR(255)    NOT NULL,
    actor_id             VARCHAR(255)    NOT NULL,
    patient_id           VARCHAR(255),
    encounter_id         VARCHAR(255),
    state_json           JSONB           NOT NULL DEFAULT '{}'::jsonb,
    current_step_order   INTEGER         NOT NULL DEFAULT 0,
    status               VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    started_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ
);

CREATE INDEX idx_path_sess_actor ON clinical.pathway_sessions (tenant_id, actor_id, status);

-- ---------------------------------------------------------------------------
-- Human-in-the-loop extraction pipeline
-- ---------------------------------------------------------------------------
CREATE TABLE clinical.extraction_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID            NOT NULL REFERENCES clinical.source_documents(id),
    job_type            VARCHAR(64)     NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'QUEUED',
    proposed_items_json JSONB           NOT NULL DEFAULT '[]'::jsonb,
    error_message       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE TABLE clinical.knowledge_review_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extraction_job_id       UUID REFERENCES clinical.extraction_jobs(id),
    proposed_payload        JSONB           NOT NULL,
    review_status           VARCHAR(32)     NOT NULL DEFAULT 'PROPOSED',
    reviewer_notes          TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    decided_at              TIMESTAMPTZ
);

CREATE INDEX idx_kri_status ON clinical.knowledge_review_items (review_status);

-- ---------------------------------------------------------------------------
-- Audit & governance
-- ---------------------------------------------------------------------------
CREATE TABLE clinical.recommendation_traces (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 VARCHAR(255)    NOT NULL,
    role                    VARCHAR(64)     NOT NULL,
    patient_id              VARCHAR(255),
    encounter_id            VARCHAR(255),
    request_type            VARCHAR(64)     NOT NULL,
    input_context_json      JSONB           NOT NULL,
    recommendations_json  JSONB           NOT NULL,
    source_refs_json        JSONB           NOT NULL DEFAULT '[]'::jsonb,
    rules_triggered_json    JSONB           NOT NULL DEFAULT '[]'::jsonb,
    knowledge_version       VARCHAR(64),
    source_version          VARCHAR(64),
    support_mode            VARCHAR(64)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_user_time ON clinical.recommendation_traces (user_id, created_at DESC);
CREATE INDEX idx_rt_patient ON clinical.recommendation_traces (patient_id, created_at DESC);

CREATE TABLE clinical.override_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_trace_id   UUID            NOT NULL REFERENCES clinical.recommendation_traces(id),
    override_reason             TEXT            NOT NULL,
    overridden_by               VARCHAR(255)    NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE clinical.event_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(128)    NOT NULL,
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id  UUID            NOT NULL DEFAULT gen_random_uuid(),
    producer        VARCHAR(128)    NOT NULL DEFAULT 'clinical-knowledge-platform-service',
    tenant_id       VARCHAR(255)    NOT NULL,
    subject_type    VARCHAR(128),
    subject_id      VARCHAR(255),
    payload         JSONB           NOT NULL,
    meta            JSONB           NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_ck_outbox_unpub ON clinical.event_outbox (published_at) WHERE published_at IS NULL;
