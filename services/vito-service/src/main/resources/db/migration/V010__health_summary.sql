-- =============================================================================
-- VITO — Secure Health Summary (SHS) Credentials
-- JWS/Verifiable Credential snapshots packed for offline card use.
-- =============================================================================

CREATE TABLE vito.health_summary_credential (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    health_id           UUID         NOT NULL REFERENCES vito.client(health_id),
    card_id             BIGINT       REFERENCES vito.smart_card(id),
    credential_type     VARCHAR(50)  NOT NULL,           -- PATIENT_SUMMARY, IMMUNIZATION, ALLERGY
    fhir_bundle_ref     VARCHAR(255),                    -- reference to BUTANO Bundle
    jws_compact         TEXT         NOT NULL,            -- JWS compact serialization
    issued_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, REVOKED
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_shs_health_id ON vito.health_summary_credential (tenant_id, health_id, status);
CREATE INDEX idx_shs_card      ON vito.health_summary_credential (card_id)
    WHERE card_id IS NOT NULL;
