-- Add virtual encounter metadata and first-class referral/telehealth workflow state.

ALTER TABLE pct_encounters
    ADD COLUMN IF NOT EXISTS modality VARCHAR(20) NOT NULL DEFAULT 'in_person',
    ADD COLUMN IF NOT EXISTS virtual_mode VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_pct_encounters_tenant_modality
    ON pct_encounters (tenant_id, modality);

CREATE TABLE IF NOT EXISTS pct_referral_packages (
    referral_id                UUID            PRIMARY KEY,
    tenant_id                  UUID            NOT NULL,
    journey_id                 VARCHAR(26)     NOT NULL REFERENCES pct_journeys(journey_id),
    encounter_id               BIGINT          REFERENCES pct_encounters(id),
    patient_cpid               VARCHAR(64)     NOT NULL,
    provider_id                VARCHAR(128),
    facility_id                UUID,
    workspace_id               UUID,
    modality                   VARCHAR(20)     NOT NULL DEFAULT 'virtual',
    virtual_mode               VARCHAR(20),
    referral_package_status    VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    workflow_stage             INT             NOT NULL DEFAULT 1,
    urgency                    VARCHAR(30)     NOT NULL DEFAULT 'ROUTINE',
    specialty                  VARCHAR(128),
    clinical_question          TEXT,
    referral_letter            TEXT,
    patient_summary            TEXT,
    visit_summary              TEXT,
    attachment_document_ids    JSONB           NOT NULL DEFAULT '[]',
    routing_target             JSONB           NOT NULL DEFAULT '{}',
    consent_required           BOOLEAN         NOT NULL DEFAULT false,
    consent_status             VARCHAR(30)     NOT NULL DEFAULT 'NOT_REQUIRED',
    consent_type               VARCHAR(30),
    consent_reference          VARCHAR(128),
    mvumo_session_id           VARCHAR(128),
    tshepo_decision_id         VARCHAR(128),
    consultation_response      TEXT,
    submitted_at               TIMESTAMPTZ,
    completed_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_patient
    ON pct_referral_packages (tenant_id, patient_cpid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_encounter
    ON pct_referral_packages (tenant_id, encounter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_referrals_tenant_facility_status
    ON pct_referral_packages (tenant_id, facility_id, referral_package_status, created_at DESC);

CREATE TABLE IF NOT EXISTS pct_telehealth_sessions (
    session_id                 UUID            PRIMARY KEY,
    tenant_id                  UUID            NOT NULL,
    journey_id                 VARCHAR(26)     REFERENCES pct_journeys(journey_id),
    encounter_id               BIGINT          REFERENCES pct_encounters(id),
    referral_id                UUID            REFERENCES pct_referral_packages(referral_id),
    patient_cpid               VARCHAR(64)     NOT NULL,
    provider_id                VARCHAR(128),
    facility_id                UUID,
    workspace_id               UUID,
    session_type               VARCHAR(30)     NOT NULL DEFAULT 'VIDEO',
    virtual_mode               VARCHAR(20)     NOT NULL DEFAULT 'video',
    status                     VARCHAR(30)     NOT NULL DEFAULT 'SCHEDULED',
    consent_status             VARCHAR(30),
    channel                    VARCHAR(30),
    room_url                   TEXT,
    notes                      TEXT,
    scheduled_at               TIMESTAMPTZ,
    started_at                 TIMESTAMPTZ,
    ended_at                   TIMESTAMPTZ,
    duration_seconds           INT,
    created_at                 TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pct_telehealth_tenant_patient
    ON pct_telehealth_sessions (tenant_id, patient_cpid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pct_telehealth_tenant_facility_status
    ON pct_telehealth_sessions (tenant_id, facility_id, status, created_at DESC);
