-- =============================================================================
-- VITO — Biometric Template Storage (ITU-T X.1081)
-- Templates stored as opaque blobs, strictly decoupled from demographics.
-- Linked to client via health_id only. FHIR Binary resource pattern.
-- =============================================================================

CREATE TABLE vito.biometric_template (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    health_id       UUID         NOT NULL REFERENCES vito.client(health_id),
    modality        VARCHAR(30)  NOT NULL,   -- FINGERPRINT, IRIS, FACE
    position        VARCHAR(30),             -- LEFT_INDEX, RIGHT_THUMB, LEFT_EYE, etc.
    template_hash   VARCHAR(128) NOT NULL,   -- SHA-256 of template bytes (integrity)
    template_data   BYTEA        NOT NULL,   -- encrypted template blob
    quality_score   INTEGER,                 -- NFIQ2 or equivalent (0-100)
    capture_device  VARCHAR(100),            -- scanner model/serial
    captured_by     VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUPERSEDED, REVOKED
    captured_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    superseded_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_biometric_position UNIQUE (tenant_id, health_id, modality, position)
        WHERE (status = 'ACTIVE')
);

-- Partial unique index: one active template per modality+position per client
CREATE UNIQUE INDEX idx_active_biometric
    ON vito.biometric_template (tenant_id, health_id, modality, position)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_biometric_health_id ON vito.biometric_template (tenant_id, health_id, status);
