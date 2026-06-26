-- ============================================================================
-- patient-safety-service V001 — Initial schema (pharmacovigilance)
-- Provides: ps_event_outbox (v1.1 envelope), idempotency_keys, and the ADR/AEFI
-- safety-report + regulatory safety-case domain. Table prefix: ps_.
--
-- Doctrine honesty: NO automated external submission lives here. VigiFlow entry is
-- recorded as MANUAL; E2B(R3) packages are labelled "aligned" (not certified) and
-- carry an explicit adapter-enabled flag so the UI can show "export pending / adapter
-- not configured" rather than "submitted".
-- ============================================================================

-- v1.1 Event Outbox (canonical schema per EVENTING_AND_TOPICS.md §2.2)
CREATE TABLE ps_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'patient-safety-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    publish_error       TEXT,
    retry_count         INT             NOT NULL DEFAULT 0,
    CONSTRAINT uq_ps_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_ps_outbox_unpublished
    ON ps_event_outbox (created_at)
    WHERE published_at IS NULL;

-- Idempotency keys (keyed by tenant_id + pod_id + idempotency_key per tech-companion spec)
CREATE TABLE idempotency_keys (
    tenant_id       TEXT        NOT NULL,
    pod_id          TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    request_hash    TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    CONSTRAINT pk_ps_idempotency_keys PRIMARY KEY (tenant_id, pod_id, idempotency_key)
);

-- ----------------------------------------------------------------------------
-- Safety report (header) — ADR or AEFI; the captured reporter intake
-- ----------------------------------------------------------------------------
CREATE TABLE ps_safety_report (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    pod_id                  VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    reference_code          VARCHAR(32),                                  -- e.g. PSR-2026-000123 (set on submit)
    report_type             VARCHAR(16)     NOT NULL,                     -- ADR | AEFI
    reporter_kind           VARCHAR(16)     NOT NULL,                     -- PROVIDER | CITIZEN | CAREGIVER
    source_context          VARCHAR(32)     NOT NULL DEFAULT 'STANDALONE',-- CLINICAL_ENCOUNTER | VACCINATION | SELF_SERVICE | STANDALONE
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',     -- see report lifecycle
    -- reporter
    reporter_actor_id       VARCHAR(255),
    reporter_actor_type     VARCHAR(64),
    reporter_name           VARCHAR(255),
    reporter_role           VARCHAR(128),
    reporter_facility_id    VARCHAR(255),
    reporter_phone          VARCHAR(64),
    reporter_email          VARCHAR(255),
    -- subject (patient)
    subject_cpid            VARCHAR(255),
    subject_is_self         BOOLEAN         NOT NULL DEFAULT FALSE,
    subject_initials        VARCHAR(16),
    subject_sex             VARCHAR(16),
    subject_dob             DATE,
    subject_age_value       INT,
    subject_age_unit        VARCHAR(16),
    subject_weight_kg       NUMERIC(6,2),
    -- clinical linkage
    encounter_id            VARCHAR(255),
    -- narrative & dates
    narrative               TEXT,
    onset_date              DATE,
    report_date             DATE,
    -- seriousness (ICH/WHO criteria)
    is_serious              BOOLEAN         NOT NULL DEFAULT FALSE,
    seriousness_reasons     JSONB,                                        -- ["DEATH","LIFE_THREATENING",...]
    seriousness_other_text  TEXT,
    -- form-pack provenance (forms-service owns the versioned pack)
    form_pack_key           VARCHAR(128),
    form_pack_version       VARCHAR(32),
    form_data               JSONB,
    -- audit columns
    created_by              VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    submitted_at            TIMESTAMPTZ
);

CREATE INDEX idx_ps_report_tenant ON ps_safety_report (tenant_id);
CREATE INDEX idx_ps_report_status ON ps_safety_report (tenant_id, status);
CREATE INDEX idx_ps_report_subject ON ps_safety_report (tenant_id, subject_cpid);
CREATE UNIQUE INDEX uq_ps_report_reference ON ps_safety_report (reference_code) WHERE reference_code IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Implicated products / vaccines (suspect & concomitant)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_product_exposure (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    report_id           UUID            NOT NULL REFERENCES ps_safety_report(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    product_role        VARCHAR(16)     NOT NULL DEFAULT 'SUSPECT',       -- SUSPECT | CONCOMITANT
    product_kind        VARCHAR(16)     NOT NULL DEFAULT 'DRUG',          -- DRUG | VACCINE
    product_id          VARCHAR(255),                                     -- product-registry ref (nullable)
    product_name        VARCHAR(512)    NOT NULL,
    manufacturer        VARCHAR(255),
    batch_number        VARCHAR(128),
    lot_number          VARCHAR(128),
    expiry_date         DATE,
    dose                VARCHAR(128),
    dose_unit           VARCHAR(64),
    route               VARCHAR(128),
    frequency           VARCHAR(128),
    indication          VARCHAR(512),
    therapy_start_date  DATE,
    therapy_end_date    DATE,
    dechallenge         VARCHAR(16),                                      -- POSITIVE|NEGATIVE|UNKNOWN|NA
    rechallenge         VARCHAR(16),
    -- vaccine-specific (AEFI)
    dose_number         VARCHAR(32),
    vaccination_date    DATE,
    vaccination_site    VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_exposure_report ON ps_product_exposure (report_id);
CREATE INDEX idx_ps_exposure_product ON ps_product_exposure (tenant_id, product_id);

-- ----------------------------------------------------------------------------
-- Reaction / adverse events (one report may carry several)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_safety_event (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    report_id           UUID            NOT NULL REFERENCES ps_safety_report(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    term                VARCHAR(512)    NOT NULL,                         -- reaction description (verbatim)
    meddra_code         VARCHAR(32),
    meddra_pt           VARCHAR(255),
    onset_date          DATE,
    end_date            DATE,
    severity            VARCHAR(16),                                      -- MILD | MODERATE | SEVERE
    outcome             VARCHAR(32),                                      -- RECOVERED|RECOVERING|NOT_RECOVERED|RECOVERED_WITH_SEQUELAE|FATAL|UNKNOWN
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_event_report ON ps_safety_event (report_id);

-- ----------------------------------------------------------------------------
-- Attachment references (binary lives in document-service)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_attachment_ref (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    report_id           UUID            NOT NULL REFERENCES ps_safety_report(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    document_object_id  VARCHAR(255)    NOT NULL,                         -- document-service objectId
    filename            VARCHAR(512),
    mime_type           VARCHAR(128),
    description         VARCHAR(512),
    consent_obtained    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_attachment_report ON ps_attachment_ref (report_id);

-- ----------------------------------------------------------------------------
-- Safety case (regulatory record) — opened when a report is submitted
-- ----------------------------------------------------------------------------
CREATE TABLE ps_safety_case (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    pod_id                  VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    report_id               UUID            NOT NULL REFERENCES ps_safety_report(id),
    case_reference          VARCHAR(32)     NOT NULL,                     -- PSC-2026-000123
    status                  VARCHAR(32)     NOT NULL DEFAULT 'OPEN',      -- see case lifecycle
    priority                VARCHAR(16)     NOT NULL DEFAULT 'ROUTINE',   -- ROUTINE | HIGH | URGENT
    report_type             VARCHAR(16)     NOT NULL,                     -- snapshot of report.report_type
    is_serious              BOOLEAN         NOT NULL DEFAULT FALSE,       -- snapshot of report.is_serious
    assigned_reviewer_id    VARCHAR(255),
    causality_assessment    VARCHAR(32)     NOT NULL DEFAULT 'NOT_ASSESSED', -- WHO-UMC scale
    mcaz_status             VARCHAR(255),                                 -- regulator-facing status text
    triaged_at              TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ps_case_reference UNIQUE (case_reference),
    CONSTRAINT uq_ps_case_report UNIQUE (report_id)
);

CREATE INDEX idx_ps_case_tenant ON ps_safety_case (tenant_id);
CREATE INDEX idx_ps_case_status ON ps_safety_case (tenant_id, status);
CREATE INDEX idx_ps_case_priority ON ps_safety_case (tenant_id, priority);

-- ----------------------------------------------------------------------------
-- Case action timeline (every meaningful regulator action is auditable)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_case_action (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id             UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    action_type         VARCHAR(48)     NOT NULL,                         -- TRIAGE|REVIEW_NOTE|STATUS_CHANGE|CAUSALITY_ASSESSED|FOLLOW_UP_REQUESTED|FOLLOW_UP_RECEIVED|VIGIFLOW_MANUAL_ENTRY|MARK_EXPORT_READY|INVESTIGATION_OPENED|ESCALATED
    actor_id            VARCHAR(255),
    actor_role          VARCHAR(128),
    from_status         VARCHAR(32),
    to_status           VARCHAR(32),
    note                TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_action_case ON ps_case_action (case_id, created_at);

-- ----------------------------------------------------------------------------
-- Follow-up request (issued by MCAZ, dispatched via Comms Hub / channels-service)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_follow_up_request (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id             UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    requested_by        VARCHAR(255),
    request_text        TEXT            NOT NULL,
    channel_hint        VARCHAR(32)     NOT NULL DEFAULT 'COMMS_HUB',     -- COMMS_HUB | EMAIL | PHONE
    conversation_ref    VARCHAR(255),                                     -- channels-service session id (when created)
    status              VARCHAR(16)     NOT NULL DEFAULT 'REQUESTED',     -- REQUESTED|SENT|RESPONDED|CANCELLED
    due_date            DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    responded_at        TIMESTAMPTZ
);

CREATE INDEX idx_ps_followup_case ON ps_follow_up_request (case_id);

-- ----------------------------------------------------------------------------
-- Follow-up response (attaches to the case and its originating request)
-- ----------------------------------------------------------------------------
CREATE TABLE ps_follow_up_response (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    follow_up_request_id    UUID            NOT NULL REFERENCES ps_follow_up_request(id) ON DELETE CASCADE,
    case_id                 UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    responder_id            VARCHAR(255),
    responder_kind          VARCHAR(16),                                  -- PROVIDER|CITIZEN|CAREGIVER
    response_text           TEXT            NOT NULL,
    attachment_object_id    VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_followup_resp_case ON ps_follow_up_response (case_id);

-- ----------------------------------------------------------------------------
-- Serious-AEFI investigation
-- ----------------------------------------------------------------------------
CREATE TABLE ps_aefi_investigation (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id                 UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    investigation_reference VARCHAR(32)     NOT NULL,                     -- PSI-2026-000123
    status                  VARCHAR(16)     NOT NULL DEFAULT 'REQUIRED',  -- NOT_REQUIRED|REQUIRED|PLANNED|IN_PROGRESS|INTERIM|FINAL|CLOSED
    assigned_to             VARCHAR(255),
    planned_date            DATE,
    form_pack_key           VARCHAR(128),
    field_findings          JSONB,
    final_classification    VARCHAR(64),                                  -- WHO AEFI causality category
    summary                 TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ps_investigation_reference UNIQUE (investigation_reference)
);

CREATE INDEX idx_ps_investigation_case ON ps_aefi_investigation (case_id);

-- ----------------------------------------------------------------------------
-- Manual VigiFlow submission record — HONEST: entry_mode is MANUAL; the reference id
-- is what the MCAZ officer pastes back after keying the case into VigiFlow externally.
-- ----------------------------------------------------------------------------
CREATE TABLE ps_vigiflow_submission (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id                 UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    entry_mode              VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',     -- MANUAL (no automated submission here)
    vigiflow_reference_id   VARCHAR(128)    NOT NULL,
    entered_by              VARCHAR(255),
    note                    TEXT,
    entered_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_vigiflow_case ON ps_vigiflow_submission (case_id);

-- ----------------------------------------------------------------------------
-- E2B(R3)-aligned export attempt — readiness package + honest adapter status.
-- This NEVER asserts a certified transmission. adapter_enabled defaults false.
-- ----------------------------------------------------------------------------
CREATE TABLE ps_e2b_export_attempt (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id             UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    package_format      VARCHAR(32)     NOT NULL DEFAULT 'E2B_R3_ALIGNED', -- "aligned", not certified
    status              VARCHAR(24)     NOT NULL DEFAULT 'EXPORT_READY',   -- BUILT|EXPORT_READY|DISPATCH_PENDING|DISPATCH_FAILED|NOT_CONFIGURED
    adapter_key         VARCHAR(64)     NOT NULL DEFAULT 'vigiflow',
    adapter_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    payload_summary     JSONB,
    message             VARCHAR(512),                                      -- e.g. "Adapter not configured — manual entry mode"
    created_by          VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_export_case ON ps_e2b_export_attempt (case_id);

-- ----------------------------------------------------------------------------
-- Conversation link — patient-safety's OWN linkage to a channels-service session
-- (Comms Hub conversations are owned by channels-service; this table only records
-- the reference and purpose so a case can be tied to its follow-up thread).
-- ----------------------------------------------------------------------------
CREATE TABLE ps_conversation_link (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    case_id             UUID            NOT NULL REFERENCES ps_safety_case(id) ON DELETE CASCADE,
    tenant_id           UUID            NOT NULL,
    conversation_ref    VARCHAR(255)    NOT NULL,                          -- channels-service session id
    channel_type        VARCHAR(48),
    purpose             VARCHAR(32)     NOT NULL DEFAULT 'FOLLOW_UP',      -- FOLLOW_UP | INTAKE
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ps_conversation_case ON ps_conversation_link (case_id);
