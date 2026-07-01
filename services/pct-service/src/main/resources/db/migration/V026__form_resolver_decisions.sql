-- Audit log of FormResolver resolutions (encounter form obligation set). Mirrors pct_cadre_decisions (V015).
-- This is an AUDIT LOG, not a system-of-record. in_patient_context is de-PII'd (age band/sex/pregnant/
-- programmes only) — never raw demographics.

CREATE TABLE pct_form_resolver_decisions (
    audit_ref            UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    actor_id             VARCHAR(255)    NOT NULL,
    correlation_id       UUID,
    journey_id           VARCHAR(26),
    encounter_id         VARCHAR(64),
    cadre_audit_ref      UUID,

    -- Inputs snapshot
    in_cadre             VARCHAR(128),
    in_context           VARCHAR(32),
    in_care_setting      VARCHAR(64),
    in_care_stage        VARCHAR(64),
    in_specialty         VARCHAR(64),
    in_acuity            INTEGER,
    in_patient_context   JSONB,

    -- Output: the obligation buckets (JSON arrays of form obligations)
    mandatory            JSONB           NOT NULL,
    recommended          JSONB           NOT NULL,
    optional             JSONB           NOT NULL,
    prohibited           JSONB           NOT NULL,
    countersign_required JSONB           NOT NULL,

    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_form_resolver_encounter ON pct_form_resolver_decisions (tenant_id, encounter_id);
CREATE INDEX idx_pct_form_resolver_created   ON pct_form_resolver_decisions (created_at);

COMMENT ON TABLE pct_form_resolver_decisions IS
    'Audit log of FormResolver resolutions: inputs + resolved mandatory/recommended/optional/prohibited/countersign form sets.';
