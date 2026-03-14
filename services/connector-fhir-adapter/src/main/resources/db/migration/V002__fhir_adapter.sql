-- ============================================================================
-- connector-fhir-adapter V002 — FHIR relay tables
-- ============================================================================

-- Relay destinations (configurable FHIR endpoints)
CREATE TABLE cfa_relay_destinations (
    destination_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    endpoint_url    VARCHAR(512)    NOT NULL,
    resource_types  VARCHAR(512)    DEFAULT '*',
    auth_type       VARCHAR(32)     DEFAULT 'NONE',
    auth_config     JSONB,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cfa_relay_dest_tenant ON cfa_relay_destinations (tenant_id, enabled);

-- Relay audit log (every relay decision is recorded)
CREATE TABLE cfa_relay_audit (
    audit_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    bundle_id       VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(128)    NOT NULL,
    resource_count  INT             NOT NULL DEFAULT 0,
    destination_id  UUID            REFERENCES cfa_relay_destinations(destination_id),
    destination_url VARCHAR(512)    NOT NULL,
    decision        VARCHAR(32)     NOT NULL DEFAULT 'ACCEPTED',
    outcome         VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    correlation_id  UUID,
    actor_ref       VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_cfa_relay_audit_tenant ON cfa_relay_audit (tenant_id, created_at DESC);
CREATE INDEX idx_cfa_relay_audit_bundle ON cfa_relay_audit (bundle_id);
