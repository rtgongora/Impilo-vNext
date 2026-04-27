-- Versioned communication / notification preferences (separate from one-off registration).
-- Append-only revisions + current pointer. No destructive updates to history.

CREATE TABLE IF NOT EXISTS mvumo.communication_preference_revision (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    patient_ref             VARCHAR(300)     NOT NULL,
    bundle_version          INT             NOT NULL,
    lifecycle_state         VARCHAR(48)     NOT NULL,
    payload_json            JSONB           NOT NULL,
    previous_revision_id    UUID            REFERENCES mvumo.communication_preference_revision (id),
    actor_ref               VARCHAR(300),
    actor_relationship      VARCHAR(80),
    source_channel          VARCHAR(64),
    reason_text             TEXT,
    effective_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at              TIMESTAMPTZ,
    review_due_at           TIMESTAMPTZ,
    assurance_method         VARCHAR(80),
    device_metadata         JSONB           NOT NULL DEFAULT '{}',
    audit_ref               VARCHAR(64),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mvumo_commpref_rev_tenant_patient
    ON mvumo.communication_preference_revision (tenant_id, patient_ref, created_at DESC);

CREATE TABLE IF NOT EXISTS mvumo.communication_preference_current (
    tenant_id               UUID            NOT NULL,
    patient_ref             VARCHAR(300)     NOT NULL,
    current_revision_id     UUID            NOT NULL REFERENCES mvumo.communication_preference_revision (id),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, patient_ref)
);

COMMENT ON TABLE mvumo.communication_preference_revision IS 'Append-only history of patient communication/notification/follow-up preferences.';
COMMENT ON TABLE mvumo.communication_preference_current IS 'Pointer to the latest active preference revision per patient.';
