-- =============================================================================
-- Msika Apps — Capability Marketplace — Initial schema
-- =============================================================================

-- ─── Publishers ─────────────────────────────────────────────────────────────
CREATE TABLE ma_publishers (
    id                      VARCHAR(64)  NOT NULL PRIMARY KEY,
    name                    VARCHAR(256) NOT NULL,
    legal_name              VARCHAR(512) NOT NULL,
    website_url             VARCHAR(1024),
    technical_contact_name  VARCHAR(256),
    technical_contact_email VARCHAR(256),
    technical_contact_phone VARCHAR(64),
    data_protection_email   VARCHAR(256),
    support_email           VARCHAR(256),
    status                  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ─── Marketplace items (capability catalogue) ──────────────────────────────
CREATE TABLE ma_marketplace_items (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    item_code                       VARCHAR(128) NOT NULL UNIQUE,
    name                            VARCHAR(256) NOT NULL,
    description                     TEXT NOT NULL,
    type                            VARCHAR(32)  NOT NULL,    -- APP, EXTENSION, PLUGIN, CONNECTOR, ADAPTER, WORKFLOW_PACK, CONTENT_PACK, AI_SKILL, DEVICE_INTEGRATION
    category                        VARCHAR(64)  NOT NULL,
    publisher_id                    VARCHAR(64)  NOT NULL REFERENCES ma_publishers(id),
    version                         VARCHAR(32)  NOT NULL,
    compatibility_version           VARCHAR(32)  NOT NULL,
    supported_environments          VARCHAR(64)  NOT NULL,    -- CSV: SANDBOX,STAGING,PRODUCTION
    visibility                      VARCHAR(32)  NOT NULL DEFAULT 'PUBLIC',
    icon_ref                        VARCHAR(256),
    documentation_url               VARCHAR(1024),
    manifest_ref                    VARCHAR(512) NOT NULL,
    security_classification         VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    data_protection_classification  VARCHAR(16)  NOT NULL DEFAULT 'NO_PHI',
    clinical_safety_classification  VARCHAR(32),
    approval_status                 VARCHAR(16)  NOT NULL DEFAULT 'APPROVED',
    certification_status            VARCHAR(32),
    regulatory_status               VARCHAR(64),
    pricing_model                   VARCHAR(32),
    licensing_model                 VARCHAR(64),
    required_scopes                 TEXT,
    required_events                 TEXT,
    required_apis                   TEXT,
    required_data_categories        TEXT,
    required_permissions            TEXT,
    facility_type_whitelist         TEXT,
    roles_allowed                   TEXT,
    consent_requirements            TEXT,
    health_status                   VARCHAR(16)  NOT NULL DEFAULT 'HEALTHY',
    last_updated                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deprecation_date                TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ma_items_type        ON ma_marketplace_items (type);
CREATE INDEX idx_ma_items_category    ON ma_marketplace_items (category);
CREATE INDEX idx_ma_items_visibility  ON ma_marketplace_items (visibility);
CREATE INDEX idx_ma_items_publisher   ON ma_marketplace_items (publisher_id);

-- ─── Activation requests ───────────────────────────────────────────────────
CREATE TABLE ma_activation_requests (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    item_id                         VARCHAR(36)  NOT NULL REFERENCES ma_marketplace_items(id),
    requester_actor_id              VARCHAR(64)  NOT NULL,
    requester_tenant_id             VARCHAR(64)  NOT NULL,
    requester_facility_id           VARCHAR(64),
    requester_workspace_id          VARCHAR(64),
    requester_programme_id          VARCHAR(64),
    purpose_of_use                  VARCHAR(32)  NOT NULL,
    justification                   TEXT NOT NULL,
    requested_configuration_json    TEXT,
    status                          VARCHAR(16)  NOT NULL DEFAULT 'REQUESTED',
    reviewers_json                  TEXT,                  -- JSON array of ApprovalRecord
    decided_at                      TIMESTAMPTZ,
    decision_reason                 TEXT,
    correlation_id                  VARCHAR(64)  NOT NULL,
    pod_id                          VARCHAR(64)  NOT NULL,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ma_act_req_item    ON ma_activation_requests (item_id);
CREATE INDEX idx_ma_act_req_status  ON ma_activation_requests (status);
CREATE INDEX idx_ma_act_req_tenant  ON ma_activation_requests (requester_tenant_id);

-- ─── Installations (per-tenant per-facility activation state) ──────────────
CREATE TABLE ma_installations (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    item_id                         VARCHAR(36)  NOT NULL REFERENCES ma_marketplace_items(id),
    item_version                    VARCHAR(32)  NOT NULL,
    tenant_id                       VARCHAR(64)  NOT NULL,
    facility_ids                    TEXT,
    workspace_ids                   TEXT,
    programme_ids                   TEXT,
    roles_enabled                   TEXT,
    configuration_json              TEXT,
    integration_contract_id         VARCHAR(36),
    external_app_id                 VARCHAR(36),
    lifecycle                       VARCHAR(32)  NOT NULL DEFAULT 'INSTALLED',
    installed_by                    VARCHAR(64)  NOT NULL,
    installed_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    configured_at                   TIMESTAMPTZ,
    activated_at                    TIMESTAMPTZ,
    suspended_at                    TIMESTAMPTZ,
    suspended_reason                TEXT,
    last_health_check_at            TIMESTAMPTZ,
    health_status                   VARCHAR(16)  NOT NULL DEFAULT 'HEALTHY',
    pod_id                          VARCHAR(64)  NOT NULL,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ma_install_item     ON ma_installations (item_id);
CREATE INDEX idx_ma_install_tenant   ON ma_installations (tenant_id);
CREATE INDEX idx_ma_install_lifecycle ON ma_installations (lifecycle);

-- ─── Audit log ──────────────────────────────────────────────────────────────
CREATE TABLE ma_audit_events (
    id                          VARCHAR(36)  NOT NULL PRIMARY KEY,
    item_id                     VARCHAR(36),
    installation_id             VARCHAR(36),
    activation_request_id       VARCHAR(36),
    external_app_id             VARCHAR(36),
    event_type                  VARCHAR(64)  NOT NULL,
    actor_id                    VARCHAR(64)  NOT NULL,
    actor_type                  VARCHAR(32),
    tenant_id                   VARCHAR(64)  NOT NULL,
    facility_id                 VARCHAR(64),
    reason                      TEXT,
    correlation_id              VARCHAR(64),
    occurred_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ma_audit_item    ON ma_audit_events (item_id);
CREATE INDEX idx_ma_audit_install ON ma_audit_events (installation_id);
CREATE INDEX idx_ma_audit_tenant  ON ma_audit_events (tenant_id);

-- ─── Event outbox (matches ops-instrumentation expectation) ────────────────
CREATE TABLE ma_event_outbox (
    id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    pod_id          VARCHAR(64)     NOT NULL,
    correlation_id  VARCHAR(64)     NOT NULL,
    idempotency_key VARCHAR(128),
    event_type      VARCHAR(256)    NOT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    payload_json    TEXT            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ma_outbox_unpublished ON ma_event_outbox (created_at) WHERE published_at IS NULL;
