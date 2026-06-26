-- =====================================================================================
-- Rito — Quality, Safety & Client Voice :: V001 initial schema
-- Schema: rito.  All domain tables are prefixed rit_*.
-- Owns: client voice (complaint/compliment/suggestion/survey), clinical service safety
-- incidents/near-misses, quality audits & supportive supervision, CAPA, QI/PDSA, learning
-- loops. Does NOT own pharmacovigilance (patient-safety-service), haemovigilance (madi),
-- or statutory regulatory inspection (tuso/indawo) — those are linked by id reference only.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS rito;

-- -------------------------------------------------------------------------------------
-- Configuration: SLA policies & escalation rules (seeded per tenant; drive triage)
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_sla_policy (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    policy_key                  VARCHAR(128) NOT NULL,
    name                        VARCHAR(255) NOT NULL,
    applies_to_case_type        VARCHAR(64),
    applies_to_severity         VARCHAR(32),
    acknowledge_within_minutes  INT,
    resolve_within_minutes      INT,
    escalate_after_minutes      INT,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_sla_policy_key UNIQUE (tenant_id, policy_key)
);

CREATE TABLE rito.rit_escalation_rule (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    rule_key            VARCHAR(128) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    trigger_type        VARCHAR(48) NOT NULL,  -- SLA_BREACH/SEVERITY/SENSITIVE_CATEGORY/STATUS_STUCK/MANUAL
    condition_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
    escalate_to_role    VARCHAR(128),
    escalate_to_level   VARCHAR(32),           -- FACILITY/DISTRICT/PROVINCE/NATIONAL/REGULATOR
    notify_template_key VARCHAR(128),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_escalation_rule_key UNIQUE (tenant_id, rule_key)
);

-- -------------------------------------------------------------------------------------
-- Core case aggregate — the single owning record for every client-voice / quality /
-- service-safety signal Rito owns. Lifecycle: DRAFT→SUBMITTED→ACKNOWLEDGED→TRIAGED→
-- ASSIGNED→UNDER_REVIEW→INVESTIGATION_OPEN→ACTION_REQUIRED→ESCALATED→WAITING_ON_*→
-- RESOLVED→CLOSED→REOPENED→CANCELLED.
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_case (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    case_reference              VARCHAR(48) NOT NULL,
    case_type                   VARCHAR(48) NOT NULL,  -- COMPLAINT/COMPLIMENT/.../SATISFACTION_SURVEY
    pillar                      VARCHAR(48) NOT NULL,  -- CLIENT_VOICE/CLIENT_SAFETY/SERVICE_QUALITY/QUALITY_ASSURANCE/ACCOUNTABLE_IMPROVEMENT
    category                    VARCHAR(64),           -- sensitive categories live here (SAFEGUARDING, PROVIDER_CONDUCT, ...)
    title                       VARCHAR(512),
    description                 TEXT,
    severity                    VARCHAR(32) NOT NULL DEFAULT 'MODERATE',  -- LOW/MODERATE/HIGH/CRITICAL
    status                      VARCHAR(48) NOT NULL DEFAULT 'SUBMITTED',
    source                      VARCHAR(48) NOT NULL,  -- NOMPILO/KHULUMA/FACILITY_QR/...
    anonymous                   BOOLEAN NOT NULL DEFAULT FALSE,
    sensitive                   BOOLEAN NOT NULL DEFAULT FALSE,  -- protect client/provider identity unless policy allows
    facility_id                 UUID,
    district_id                 UUID,
    province_id                 UUID,
    programme_id                UUID,
    reporter_actor_id           VARCHAR(128),
    reporter_actor_type         VARCHAR(48),
    subject_health_id           VARCHAR(128),          -- the client the case concerns (protected)
    assigned_to_actor_id        VARCHAR(128),
    assigned_to_actor_type      VARCHAR(48),
    sla_policy_id               UUID REFERENCES rito.rit_sla_policy(id),
    due_at                      TIMESTAMPTZ,
    acknowledged_at             TIMESTAMPTZ,
    triaged_at                  TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    resolution_summary          TEXT,
    closure_outcome             VARCHAR(48),           -- RESOLVED/UNSUBSTANTIATED/REFERRED/WITHDRAWN/...
    satisfaction_score          INT,                   -- post-closure CSAT 1..5
    ai_suggested_severity       VARCHAR(32),           -- advisory only; never auto-closes
    ai_suggested_routing        VARCHAR(128),
    ai_rationale                TEXT,
    linked_patient_safety_case_id   VARCHAR(128),      -- cross-SoR id reference only (no embedded copy)
    linked_haemovigilance_case_id   VARCHAR(128),
    linked_regulatory_finding_id    VARCHAR(128),
    linked_support_ticket_id        VARCHAR(128),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(128),
    CONSTRAINT uq_rit_case_reference UNIQUE (tenant_id, case_reference)
);
CREATE INDEX idx_rit_case_tenant_status ON rito.rit_case (tenant_id, status);
CREATE INDEX idx_rit_case_tenant_type ON rito.rit_case (tenant_id, case_type);
CREATE INDEX idx_rit_case_facility ON rito.rit_case (tenant_id, facility_id);
CREATE INDEX idx_rit_case_assigned ON rito.rit_case (tenant_id, assigned_to_actor_id);
CREATE INDEX idx_rit_case_due ON rito.rit_case (due_at) WHERE status NOT IN ('CLOSED','CANCELLED','RESOLVED');

CREATE TABLE rito.rit_case_party (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    party_role          VARCHAR(48) NOT NULL,  -- CLIENT/CAREGIVER/PROVIDER/FACILITY_FOCAL/SUPERVISOR/REGULATOR/SYSTEM/AGENT
    actor_id            VARCHAR(128),
    actor_type          VARCHAR(48),
    display_name        VARCHAR(255),          -- protected when identity_protected
    contact_json        JSONB,                 -- protected
    identity_protected  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_case_party_case ON rito.rit_case_party (case_id);

CREATE TABLE rito.rit_case_link (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    link_type       VARCHAR(48) NOT NULL,  -- CLIENT/HEALTH_ID/VISIT/ENCOUNTER/ORDER/...
    target_ref      VARCHAR(255) NOT NULL,
    target_system   VARCHAR(64),           -- which SoR owns the target (PCT/TUSO/MADI/...)
    note            VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_case_link_case ON rito.rit_case_link (case_id);
CREATE INDEX idx_rit_case_link_target ON rito.rit_case_link (tenant_id, link_type, target_ref);

CREATE TABLE rito.rit_case_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(48) NOT NULL,  -- SUBMITTED/ACKNOWLEDGED/TRIAGED/ASSIGNED/STATUS_CHANGED/ESCALATED/RESOLVED/CLOSED/REOPENED/NOTE
    from_status     VARCHAR(48),
    to_status       VARCHAR(48),
    actor_id        VARCHAR(128),
    actor_type      VARCHAR(48),
    note            TEXT,
    detail_json     JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_case_event_case ON rito.rit_case_event (case_id, occurred_at);

CREATE TABLE rito.rit_case_assignment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    assignee_actor_id   VARCHAR(128) NOT NULL,
    assignee_actor_type VARCHAR(48),
    assignee_role       VARCHAR(64),
    assigned_by         VARCHAR(128),
    reason              VARCHAR(512),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unassigned_at       TIMESTAMPTZ
);
CREATE INDEX idx_rit_case_assignment_case ON rito.rit_case_assignment (case_id);
CREATE INDEX idx_rit_case_assignment_active ON rito.rit_case_assignment (tenant_id, assignee_actor_id) WHERE active = TRUE;

CREATE TABLE rito.rit_case_message (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id             UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    direction           VARCHAR(16) NOT NULL,  -- INBOUND/OUTBOUND/INTERNAL
    channel             VARCHAR(48),           -- KHULUMA/PORTAL/MOBILE/SYSTEM/INTERNAL_NOTE
    author_actor_id     VARCHAR(128),
    author_actor_type   VARCHAR(48),
    visibility          VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',  -- CLIENT_VISIBLE/INTERNAL
    body                TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_case_message_case ON rito.rit_case_message (case_id, created_at);

CREATE TABLE rito.rit_case_attachment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    document_ref    VARCHAR(255) NOT NULL,  -- document-service object id
    file_name       VARCHAR(512),
    content_type    VARCHAR(128),
    size_bytes      BIGINT,
    uploaded_by     VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_case_attachment_case ON rito.rit_case_attachment (case_id);

-- -------------------------------------------------------------------------------------
-- Safety incident extension (1:1 with a rit_case of a safety case_type)
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_safety_incident (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id                 UUID NOT NULL REFERENCES rito.rit_case(id) ON DELETE CASCADE,
    tenant_id               UUID NOT NULL,
    incident_type           VARCHAR(64) NOT NULL,  -- FALL/WRONG_SITE/MEDICATION_PROCESS_ERROR/DIAGNOSTIC_DELAY/DOCUMENTATION_ERROR/EQUIPMENT/PROCESS/SAFEGUARDING/PRIVACY_CONFIDENTIALITY/OTHER
    harm_level              VARCHAR(32) NOT NULL DEFAULT 'NONE',  -- NONE/NEAR_MISS/MINOR/MODERATE/SEVERE/DEATH
    occurred_at             TIMESTAMPTZ,
    location_detail         VARCHAR(512),
    contributing_factors    JSONB,
    immediate_actions       TEXT,
    rca_method              VARCHAR(64),
    rca_summary             TEXT,
    sentinel                BOOLEAN NOT NULL DEFAULT FALSE,
    reported_within_sla     BOOLEAN,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_safety_incident_case UNIQUE (case_id)
);
CREATE INDEX idx_rit_safety_incident_tenant ON rito.rit_safety_incident (tenant_id, incident_type);

-- -------------------------------------------------------------------------------------
-- Quality signals (system / integration-sourced; reviewed then optionally converted to a case)
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_quality_signal (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    signal_type     VARCHAR(64) NOT NULL,  -- CHECKLIST_NONCOMPLIANCE/PATHWAY_DEVIATION/WAIT_TIME/TURNAROUND/DOC_INCOMPLETE/MISSED_STEP/EXTERNAL_FINDING
    source_system   VARCHAR(48) NOT NULL,  -- PCT/TUSO/INDAWO/MADI/PATIENT_SAFETY/SUPPORT/FUNDO/RULES
    source_ref      VARCHAR(255),
    severity        VARCHAR(32) NOT NULL DEFAULT 'LOW',
    facility_id     UUID,
    district_id     UUID,
    summary         VARCHAR(1024),
    detail_json     JSONB,
    status          VARCHAR(32) NOT NULL DEFAULT 'NEW',  -- NEW/REVIEWED/CONVERTED_TO_CASE/DISMISSED
    reviewed_by     VARCHAR(128),
    reviewed_at     TIMESTAMPTZ,
    case_id         UUID REFERENCES rito.rit_case(id),
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_quality_signal_status ON rito.rit_quality_signal (tenant_id, status);
CREATE INDEX idx_rit_quality_signal_source ON rito.rit_quality_signal (tenant_id, source_system);

-- -------------------------------------------------------------------------------------
-- Quality audit tools (reusable templates) + audits + sections + findings
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_audit_tool (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    tool_key            VARCHAR(128) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    version             VARCHAR(32) NOT NULL DEFAULT '1',
    audit_type          VARCHAR(48),           -- QUALITY_AUDIT/SUPPORTIVE_SUPERVISION/ACCREDITATION_READINESS/...
    form_schema_ref     VARCHAR(128),          -- forms-service form_key (optional)
    max_score           NUMERIC(10,2),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_audit_tool_key UNIQUE (tenant_id, tool_key)
);

CREATE TABLE rito.rit_audit_section (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_tool_id   UUID NOT NULL REFERENCES rito.rit_audit_tool(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    section_key     VARCHAR(128) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    ordinal         INT NOT NULL DEFAULT 0,
    weight          NUMERIC(6,2) NOT NULL DEFAULT 1,
    max_score       NUMERIC(10,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_audit_section_tool ON rito.rit_audit_section (audit_tool_id);

CREATE TABLE rito.rit_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    audit_reference     VARCHAR(48) NOT NULL,
    audit_tool_id       UUID REFERENCES rito.rit_audit_tool(id),
    audit_type          VARCHAR(48) NOT NULL,  -- QUALITY_AUDIT/SUPPORTIVE_SUPERVISION/ACCREDITATION_READINESS/SITE_REVIEW/SELF_ASSESSMENT
    facility_id         UUID,
    district_id         UUID,
    province_id         UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'PLANNED',  -- PLANNED/IN_PROGRESS/SUBMITTED/CLOSED/CANCELLED
    lead_auditor_actor_id   VARCHAR(128),
    scheduled_at        TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    score_numerator     NUMERIC(10,2),
    score_denominator   NUMERIC(10,2),
    score_percent       NUMERIC(6,2),
    summary             TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(128),
    CONSTRAINT uq_rit_audit_reference UNIQUE (tenant_id, audit_reference)
);
CREATE INDEX idx_rit_audit_tenant_status ON rito.rit_audit (tenant_id, status);
CREATE INDEX idx_rit_audit_facility ON rito.rit_audit (tenant_id, facility_id);

CREATE TABLE rito.rit_audit_finding (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id                UUID NOT NULL REFERENCES rito.rit_audit(id) ON DELETE CASCADE,
    tenant_id               UUID NOT NULL,
    section_key             VARCHAR(128),
    item_ref                VARCHAR(128),
    finding_type            VARCHAR(32) NOT NULL,  -- COMPLIANT/NON_COMPLIANT/PARTIAL/NOT_APPLICABLE/OBSERVATION
    severity                VARCHAR(32),
    description             TEXT,
    evidence                TEXT,
    recommended_action      TEXT,
    score                   NUMERIC(10,2),
    corrective_action_id    UUID,
    case_id                 UUID REFERENCES rito.rit_case(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_audit_finding_audit ON rito.rit_audit_finding (audit_id);

-- -------------------------------------------------------------------------------------
-- Accountable improvement: corrective/preventive actions (CAPA) + QI/PDSA plans & tasks
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_corrective_action (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    ca_reference            VARCHAR(48) NOT NULL,
    action_type             VARCHAR(16) NOT NULL DEFAULT 'CORRECTIVE',  -- CORRECTIVE/PREVENTIVE
    title                   VARCHAR(512) NOT NULL,
    description             TEXT,
    source_type             VARCHAR(48),   -- CASE/AUDIT_FINDING/SAFETY_INCIDENT/QI_PLAN/QUALITY_SIGNAL
    source_ref              VARCHAR(128),
    case_id                 UUID REFERENCES rito.rit_case(id),
    audit_finding_id        UUID REFERENCES rito.rit_audit_finding(id),
    facility_id             UUID,
    owner_actor_id          VARCHAR(128),
    owner_actor_type        VARCHAR(48),
    priority                VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    status                  VARCHAR(32) NOT NULL DEFAULT 'OPEN',  -- OPEN/IN_PROGRESS/COMPLETED/VERIFIED/CANCELLED/OVERDUE
    due_at                  TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    completed_by            VARCHAR(128),
    completion_notes        TEXT,
    verified_at             TIMESTAMPTZ,
    verified_by             VARCHAR(128),
    verification_outcome    VARCHAR(32),   -- EFFECTIVE/NOT_EFFECTIVE/PARTIAL
    verification_notes      TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(128),
    CONSTRAINT uq_rit_corrective_action_reference UNIQUE (tenant_id, ca_reference)
);
CREATE INDEX idx_rit_corrective_action_status ON rito.rit_corrective_action (tenant_id, status);
CREATE INDEX idx_rit_corrective_action_case ON rito.rit_corrective_action (case_id);
CREATE INDEX idx_rit_corrective_action_owner ON rito.rit_corrective_action (tenant_id, owner_actor_id);

CREATE TABLE rito.rit_qi_plan (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    qi_reference        VARCHAR(48) NOT NULL,
    title               VARCHAR(512) NOT NULL,
    aim_statement       TEXT,
    methodology         VARCHAR(48) NOT NULL DEFAULT 'PDSA',  -- PDSA/MODEL_FOR_IMPROVEMENT/LEAN/OTHER
    problem_statement   TEXT,
    baseline_measure    NUMERIC(14,4),
    target_measure      NUMERIC(14,4),
    measure_unit        VARCHAR(64),
    facility_id         UUID,
    district_id         UUID,
    sponsor_actor_id    VARCHAR(128),
    lead_actor_id       VARCHAR(128),
    source_case_id      UUID REFERENCES rito.rit_case(id),
    source_finding_id   UUID REFERENCES rito.rit_audit_finding(id),
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/ACTIVE/COMPLETED/CANCELLED
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    outcome_summary     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(128),
    CONSTRAINT uq_rit_qi_plan_reference UNIQUE (tenant_id, qi_reference)
);
CREATE INDEX idx_rit_qi_plan_status ON rito.rit_qi_plan (tenant_id, status);

CREATE TABLE rito.rit_qi_task (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    qi_plan_id      UUID NOT NULL REFERENCES rito.rit_qi_plan(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    title           VARCHAR(512) NOT NULL,
    description     TEXT,
    pdsa_stage      VARCHAR(16),   -- PLAN/DO/STUDY/ACT
    owner_actor_id  VARCHAR(128),
    status          VARCHAR(32) NOT NULL DEFAULT 'TODO',  -- TODO/IN_PROGRESS/DONE/BLOCKED/CANCELLED
    ordinal         INT NOT NULL DEFAULT 0,
    due_at          TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_qi_task_plan ON rito.rit_qi_task (qi_plan_id);

-- -------------------------------------------------------------------------------------
-- Experience / satisfaction surveys + responses
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_survey (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    survey_key          VARCHAR(128) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    survey_type         VARCHAR(48) NOT NULL DEFAULT 'CSAT',  -- CSAT/NPS/POST_VISIT/PATIENT_EXPERIENCE/CUSTOM
    form_schema_ref     VARCHAR(128),  -- forms-service form_key (optional)
    questions_json      JSONB NOT NULL DEFAULT '[]'::jsonb,
    anonymous_allowed   BOOLEAN NOT NULL DEFAULT TRUE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_survey_key UNIQUE (tenant_id, survey_key)
);

CREATE TABLE rito.rit_survey_response (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    survey_id               UUID NOT NULL REFERENCES rito.rit_survey(id) ON DELETE CASCADE,
    tenant_id               UUID NOT NULL,
    respondent_actor_id     VARCHAR(128),
    respondent_actor_type   VARCHAR(48),
    anonymous               BOOLEAN NOT NULL DEFAULT FALSE,
    facility_id             UUID,
    visit_ref               VARCHAR(255),
    answers_json            JSONB NOT NULL DEFAULT '{}'::jsonb,
    score                   NUMERIC(10,2),
    nps_category            VARCHAR(16),  -- PROMOTER/PASSIVE/DETRACTOR
    free_text               TEXT,
    case_id                 UUID REFERENCES rito.rit_case(id),  -- if a low score spawned a case
    submitted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_survey_response_survey ON rito.rit_survey_response (survey_id);
CREATE INDEX idx_rit_survey_response_facility ON rito.rit_survey_response (tenant_id, facility_id);

-- -------------------------------------------------------------------------------------
-- Transactional outbox (relayed by RitoOutboxPublisher)
-- -------------------------------------------------------------------------------------
CREATE TABLE rito.rit_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'rito-quality-safety-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_rit_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_rit_outbox_unpublished ON rito.rit_outbox (created_at) WHERE published_at IS NULL;
