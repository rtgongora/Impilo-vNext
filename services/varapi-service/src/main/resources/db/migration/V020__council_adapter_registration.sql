-- =============================================================================
-- Varapi V020 — LIVE council / HPA regulatory verification ADAPTER SEAM
--                (IATG Wave 2, Channel A)
--
-- Wave 1 ingests council data only via manual bulk upload into
-- varapi.provider_council_registration_record. This migration adds the seam for an
-- OPTIONAL live authority call that sits in front of that local imported registry.
--
-- Honestly gated OFF: the global flag varapi.council-regulatory.live-adapter-enabled
-- defaults false AND a registration row must be enabled = true before any live call is
-- made. No verification result is ever fabricated — an unreachable authority yields an
-- honest degraded outcome, never a PASS.
--
-- Shape mirrors the connector-fhir-adapter cfa_relay_destinations / cfa_relay_audit
-- pattern (endpoint_url, auth_type default NONE, auth_config jsonb, enabled boolean).
-- =============================================================================

-- Per-council live verification endpoint registration.
CREATE TABLE IF NOT EXISTS varapi.council_adapter_registration (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    council_code        VARCHAR(64)     NOT NULL,   -- e.g. MDPCZ, NMCZ, AHPCZ
    endpoint_url        VARCHAR(512)    NOT NULL,
    auth_type           VARCHAR(32)     NOT NULL DEFAULT 'NONE',
    auth_config         JSONB,
    enabled             BOOLEAN         NOT NULL DEFAULT FALSE,  -- OFF unless explicitly enabled
    connect_timeout_ms  INTEGER         NOT NULL DEFAULT 3000,
    read_timeout_ms     INTEGER         NOT NULL DEFAULT 5000,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- At most one live endpoint per tenant + council.
CREATE UNIQUE INDEX IF NOT EXISTS uq_council_adapter_registration_tenant_council
    ON varapi.council_adapter_registration (tenant_id, council_code);

CREATE INDEX IF NOT EXISTS idx_council_adapter_registration_enabled
    ON varapi.council_adapter_registration (tenant_id, council_code, enabled);

COMMENT ON TABLE varapi.council_adapter_registration IS
    'Channel A live council/HPA verification endpoints (IATG Wave 2). Gated OFF: enabled defaults false and the global live-adapter-enabled flag defaults false.';

-- Audit trail of every live adapter call (mirrors cfa_relay_audit intent).
CREATE TABLE IF NOT EXISTS varapi.council_adapter_call_audit (
    id                              BIGSERIAL       PRIMARY KEY,
    tenant_id                       UUID            NOT NULL,
    council_code                    VARCHAR(64)     NOT NULL,
    endpoint_url                    VARCHAR(512),
    temporary_provider_public_id    VARCHAR(64),
    result                          VARCHAR(32)     NOT NULL,   -- IDENTITY_MATCH | REGISTRY_MATCH | NO_MATCH | FORMAT_INVALID | UNREACHABLE
    reachable                       BOOLEAN         NOT NULL,   -- false => honest degraded, never a PASS
    confidence                      NUMERIC(5,2),               -- the AUTHORITY's graded score, never platform-fabricated
    matched_ref                     VARCHAR(128),               -- matching evidence, never identity
    notes                           VARCHAR(255),
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_council_adapter_call_audit_tenant
    ON varapi.council_adapter_call_audit (tenant_id, created_at DESC);

COMMENT ON TABLE varapi.council_adapter_call_audit IS
    'One row per live council adapter invocation (IATG Wave 2). confidence is the authority score; unreachable calls are recorded honestly and never yield a PASS.';
