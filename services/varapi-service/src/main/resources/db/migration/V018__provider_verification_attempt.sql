-- =============================================================================
-- Varapi V018 — Provider verification attempt evidence trail (IATG Wave 1)
--
-- Every ProviderTrustService transition (upgrade, reaffirmation, or explicit
-- evidence-revoked downgrade) writes exactly one row here. Shape cloned from
-- varapi.external_provider_verification_attempt (V011), anchored on the
-- canonical varapi.provider row instead of the external collaboration profile.
--
-- evidence_ref is matching evidence (council record / document / employment
-- reference), NOT identity — it is masked in every API response.
-- =============================================================================

CREATE TABLE IF NOT EXISTS varapi.provider_verification_attempt (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    provider_id     BIGINT          NOT NULL REFERENCES varapi.provider (id),
    evidence_type   VARCHAR(64)     NOT NULL,   -- e.g. COUNCIL_REGISTRATION, EMPLOYMENT_RECORD, EVIDENCE_REVOKED
    evidence_source VARCHAR(64)     NOT NULL,   -- COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION
    outcome         VARCHAR(40)     NOT NULL,   -- e.g. TRUST_UPGRADED | TRUST_REAFFIRMED | TRUST_REVOKED | RECORDED
    confidence      NUMERIC(5,2),
    evidence_ref    VARCHAR(255),
    attempted_by    VARCHAR(255),
    attempted_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_provider_verification_attempt_provider
    ON varapi.provider_verification_attempt (tenant_id, provider_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_provider_verification_attempt_source
    ON varapi.provider_verification_attempt (tenant_id, evidence_source, outcome);

COMMENT ON TABLE varapi.provider_verification_attempt IS
    'Evidence trail for platform provider trust transitions (IATG Wave 1). One row per ProviderTrustService transition; evidence_ref is matching evidence, never identity, and is masked in API payloads.';
