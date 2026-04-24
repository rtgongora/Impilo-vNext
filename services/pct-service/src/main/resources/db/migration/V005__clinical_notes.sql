-- Strangler clinical notes store with optional patient-share / provisional provider provenance.

CREATE TABLE pct_clinical_notes (
    id                          BIGSERIAL PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    patient_id                  VARCHAR(128)    NOT NULL,
    patient_health_id           UUID,
    encounter_id                UUID,
    note_type                   VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    body                        TEXT,
    subjective                  TEXT,
    objective                   TEXT,
    assessment                  TEXT,
    plan                        TEXT,
    author_id                   VARCHAR(255)    NOT NULL,
    author_name                 VARCHAR(255),
    patient_share_grant_id      BIGINT,
    vito_contribution_id        BIGINT,
    temporary_provider_public_id VARCHAR(26),
    trust_level_at_authoring    VARCHAR(64),
    share_correlation_id        VARCHAR(64),
    provenance                  JSONB,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_clinical_notes_tenant_patient ON pct_clinical_notes (tenant_id, patient_id);
CREATE INDEX idx_pct_clinical_notes_grant ON pct_clinical_notes (patient_share_grant_id);
CREATE INDEX idx_pct_clinical_notes_health ON pct_clinical_notes (tenant_id, patient_health_id);

COMMENT ON COLUMN pct_clinical_notes.patient_share_grant_id IS
    'VITO patient_share_access_grant.id when the note was created from a patient-mediated share flow.';
COMMENT ON COLUMN pct_clinical_notes.vito_contribution_id IS
    'VITO patient_share_contribution.id for cross-service audit linkage.';
