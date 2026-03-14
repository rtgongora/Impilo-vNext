-- developer-portal-service: client registration, API key management, sandbox config
-- Table prefix: dvp_

CREATE TABLE dvp_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    schema_version  VARCHAR(16)     NOT NULL DEFAULT '1',
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255),
    producer        VARCHAR(128)    NOT NULL DEFAULT 'developer-portal-service',
    tenant_id       UUID,
    pod_id          VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255),
    subject_type    VARCHAR(128),
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    payload_json    TEXT,
    partition_key   VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_dvp_outbox_unpublished ON dvp_event_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TABLE dvp_idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_hash    VARCHAR(64),
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT (now() + INTERVAL '24 hours'),
    CONSTRAINT uq_dvp_idempotency UNIQUE (idempotency_key)
);

-- Developer clients (registered API consumers)
CREATE TABLE dvp_clients (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    client_name     VARCHAR(255)    NOT NULL,
    description     TEXT,
    contact_email   VARCHAR(255),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    sandbox_enabled BOOLEAN         NOT NULL DEFAULT false,
    sandbox_config  JSONB,
    deprecation_posture VARCHAR(32) NOT NULL DEFAULT 'NONE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dvp_clients_tenant ON dvp_clients (tenant_id);

-- API keys (issued to developer clients)
CREATE TABLE dvp_api_keys (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID            NOT NULL REFERENCES dvp_clients(id),
    key_prefix      VARCHAR(8)      NOT NULL,
    key_hash        VARCHAR(128)    NOT NULL,
    label           VARCHAR(128),
    scopes          TEXT[],
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    last_used_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    rotated_from_id UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dvp_api_keys_client ON dvp_api_keys (client_id);
CREATE INDEX idx_dvp_api_keys_prefix ON dvp_api_keys (key_prefix);

-- Sandbox configurations
CREATE TABLE dvp_sandbox_configs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID            NOT NULL REFERENCES dvp_clients(id),
    environment     VARCHAR(32)     NOT NULL DEFAULT 'SANDBOX',
    rate_limit_rpm  INT             NOT NULL DEFAULT 60,
    allowed_endpoints TEXT[],
    mock_responses  JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dvp_sandbox_client ON dvp_sandbox_configs (client_id);
