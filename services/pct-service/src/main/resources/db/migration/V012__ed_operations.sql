-- =============================================================================
-- PCT V012 — Full Emergency Department / Casualty operations SoR
-- Visit lifecycle, structured triage, trauma surveys, paging, disposition coding
-- =============================================================================

CREATE TABLE pct.ed_visit (
    visit_id                UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    facility_id             UUID            NOT NULL,
    journey_id              VARCHAR(26)     NOT NULL UNIQUE REFERENCES pct_journeys(journey_id),
    patient_cpid            VARCHAR(64)     NOT NULL,
    emergency_case_id       UUID,
    arrival_mode            VARCHAR(32)     NOT NULL DEFAULT 'WALK_IN',
    ambulance_call_sign     VARCHAR(64),
    chief_complaint         TEXT,
    presenting_problem_code VARCHAR(64),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'REGISTERED',
    zone                    VARCHAR(32),
    track_number            VARCHAR(16),
    assigned_clinician      VARCHAR(128),
    pathway_ref             VARCHAR(128),
    protocol_ref            VARCHAR(128),
    pathway_session_id      UUID,
    encounter_id            BIGINT,
    current_acuity          INT,
    news2_score             INT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ed_visit_tenant_facility_status ON pct.ed_visit (tenant_id, facility_id, status);
CREATE INDEX idx_ed_visit_tenant_cpid ON pct.ed_visit (tenant_id, patient_cpid);
CREATE INDEX idx_ed_visit_journey ON pct.ed_visit (journey_id);

CREATE TABLE pct.ed_triage_assessment (
    id                      BIGSERIAL       PRIMARY KEY,
    visit_id                UUID            NOT NULL REFERENCES pct.ed_visit(visit_id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    acuity                  INT             NOT NULL CHECK (acuity BETWEEN 1 AND 5),
    triage_system           VARCHAR(32)     NOT NULL DEFAULT 'IMPILO_5',
    chief_complaint         TEXT,
    presenting_problem_code VARCHAR(64),
    pain_score              INT,
    vitals                  JSONB           DEFAULT '{}',
    danger_signs            JSONB           DEFAULT '[]',
    discriminators          JSONB           DEFAULT '{}',
    news2_score             INT,
    fast_track              BOOLEAN         NOT NULL DEFAULT FALSE,
    notes                   TEXT,
    triaged_by              VARCHAR(128)    NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ed_triage_visit ON pct.ed_triage_assessment (visit_id, created_at DESC);

CREATE TABLE pct.ed_trauma_activation (
    trauma_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id                UUID            NOT NULL REFERENCES pct.ed_visit(visit_id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    trauma_level            INT             NOT NULL CHECK (trauma_level BETWEEN 1 AND 3),
    mechanism               VARCHAR(64)     NOT NULL,
    team_leader             VARCHAR(128),
    team_assignments        JSONB           DEFAULT '[]',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    activated_by            VARCHAR(128)    NOT NULL,
    activated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ended_at                TIMESTAMPTZ,
    outcome                 VARCHAR(64),
    notes                   TEXT
);

CREATE INDEX idx_ed_trauma_visit ON pct.ed_trauma_activation (visit_id, activated_at DESC);

CREATE TABLE pct.ed_trauma_survey (
    id                      BIGSERIAL       PRIMARY KEY,
    trauma_id               UUID            NOT NULL REFERENCES pct.ed_trauma_activation(trauma_id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    survey_type             VARCHAR(32)     NOT NULL,
    section_key             VARCHAR(32),
    checklist               JSONB           DEFAULT '{}',
    vitals                  JSONB           DEFAULT '{}',
    notes                   TEXT,
    recorded_by             VARCHAR(128)    NOT NULL,
    recorded_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ed_trauma_survey_trauma ON pct.ed_trauma_survey (trauma_id, recorded_at);

CREATE TABLE pct.ed_disposition (
    disposition_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id                UUID            NOT NULL UNIQUE REFERENCES pct.ed_visit(visit_id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    disposition_type        VARCHAR(32)     NOT NULL,
    primary_diagnosis_code  VARCHAR(32),
    primary_diagnosis_display VARCHAR(512),
    secondary_diagnoses     JSONB           DEFAULT '[]',
    external_cause_code     VARCHAR(32),
    external_cause_display  VARCHAR(512),
    procedure_codes         JSONB           DEFAULT '[]',
    discharge_summary       TEXT,
    destination_facility    VARCHAR(256),
    destination_ward        VARCHAR(128),
    disposition_by          VARCHAR(128)    NOT NULL,
    disposition_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE pct.ed_clinical_page (
    page_id                 UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id                UUID            NOT NULL REFERENCES pct.ed_visit(visit_id) ON DELETE CASCADE,
    tenant_id               UUID            NOT NULL,
    page_type               VARCHAR(32)     NOT NULL,
    urgency                 VARCHAR(16)     NOT NULL DEFAULT 'STAT',
    recipient_role          VARCHAR(64),
    recipient_id            VARCHAR(128),
    message                 TEXT            NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    support_ticket_ref      VARCHAR(128),
    notification_ref        VARCHAR(128),
    sent_by                 VARCHAR(128)    NOT NULL,
    sent_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    acknowledged_at         TIMESTAMPTZ
);

CREATE INDEX idx_ed_page_visit ON pct.ed_clinical_page (visit_id, sent_at DESC);
CREATE INDEX idx_ed_page_status ON pct.ed_clinical_page (tenant_id, status);

-- Link emergency_cases to journeys when reconciled
ALTER TABLE pct.emergency_cases ADD COLUMN IF NOT EXISTS journey_id VARCHAR(26);
ALTER TABLE pct.emergency_cases ADD COLUMN IF NOT EXISTS visit_id UUID;
