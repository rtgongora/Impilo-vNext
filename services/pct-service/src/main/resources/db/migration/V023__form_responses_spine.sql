-- Encounter-bound structured form RESPONSES (PCT is SoR).
--
-- Form DEFINITIONS + immutable versions live in forms-service (fs_form_schema_versions). PCT never
-- re-persists a definition; it version-LOCKS against the immutable forms-service version id
-- (form_schema_version_id) captured at DRAFT. This is the encounter-child pattern already used by
-- pct_problems / pct_care_plans (V017): encounter_id is VARCHAR(64), answers are JSONB.

CREATE TABLE pct_form_response (
    response_id             UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    subject_cpid            VARCHAR(128)    NOT NULL,
    journey_id              VARCHAR(26),
    encounter_id            VARCHAR(64),
    encounter_ref           UUID,
    butano_encounter_ref    VARCHAR(128),

    -- Definition binding (version-locked at DRAFT; immutable thereafter)
    form_key                VARCHAR(128)    NOT NULL,
    form_schema_id          VARCHAR(36)     NOT NULL,
    form_version            INTEGER         NOT NULL,
    form_schema_version_id  VARCHAR(36)     NOT NULL,
    form_title              VARCHAR(256),

    -- Lifecycle
    status                  VARCHAR(24)     NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | IN_PROGRESS | SUBMITTED | AMENDED | VOIDED | SUPERSEDED
    obligation              VARCHAR(24),        -- resolver verdict: MANDATORY|RECOMMENDED|OPTIONAL|COUNTERSIGN_REQUIRED
    countersign_required    BOOLEAN         NOT NULL DEFAULT false,
    resolver_audit_ref      UUID,

    -- Answers (JSONB keyed by field linkId) + optional canonical FHIR QuestionnaireResponse projection
    answers                 JSONB           NOT NULL DEFAULT '{}',
    questionnaire_response  JSONB,

    -- Provider context at authoring time
    provider_id             VARCHAR(128),
    provider_cadre          VARCHAR(128),
    provider_role           VARCHAR(128),
    facility_id             UUID,
    workspace_id            UUID,
    device_source           VARCHAR(64),
    offline_sync_status     VARCHAR(24),

    supersedes_response_id     UUID REFERENCES pct_form_response(response_id),
    superseded_by_response_id  UUID REFERENCES pct_form_response(response_id),

    created_by              VARCHAR(255)    NOT NULL,
    submitted_by            VARCHAR(255),
    submitted_at            TIMESTAMPTZ,
    voided_by               VARCHAR(255),
    voided_at               TIMESTAMPTZ,
    void_reason             TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_form_response_subject   ON pct_form_response (tenant_id, subject_cpid);
CREATE INDEX idx_pct_form_response_encounter ON pct_form_response (tenant_id, encounter_id);
CREATE INDEX idx_pct_form_response_journey   ON pct_form_response (tenant_id, journey_id);
CREATE INDEX idx_pct_form_response_status    ON pct_form_response (tenant_id, status);
CREATE INDEX idx_pct_form_response_form      ON pct_form_response (tenant_id, form_key, form_version);

COMMENT ON TABLE pct_form_response IS
    'Encounter-bound structured form responses (PCT SoR). Definition lives in forms-service; version-locked via form_schema_version_id captured at DRAFT.';
