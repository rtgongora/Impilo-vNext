-- =============================================================================
-- Integration Hub V4 — Governance registries
--
-- Implements docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md §4–§6:
--   - External application registry
--   - Integration contract registry
--   - Service-to-service contract registry
--   - Webhook subscription registry
--   - External event subscription projection
--   - Event catalogue
--
-- All tables tenanted; auditable via the existing ih_event_outbox + audit-ledger.
-- =============================================================================

-- ─── External applications ──────────────────────────────────────────────────
CREATE TABLE ih_external_applications (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    app_code                        VARCHAR(128) NOT NULL,
    name                            VARCHAR(256) NOT NULL,
    description                     TEXT,
    publisher_id                    VARCHAR(64)  NOT NULL,
    publisher_name                  VARCHAR(256) NOT NULL,
    technical_contact_name          VARCHAR(256),
    technical_contact_email         VARCHAR(256),
    technical_contact_phone         VARCHAR(64),
    data_protection_contact_name    VARCHAR(256),
    data_protection_contact_email   VARCHAR(256),
    status                          VARCHAR(32)  NOT NULL DEFAULT 'REGISTERED',
    environment                     VARCHAR(16)  NOT NULL,
    integration_category            VARCHAR(64)  NOT NULL,
    risk_classification             VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    authentication_method           VARCHAR(64)  NOT NULL,
    oauth_client_id                 VARCHAR(256),
    oauth_client_secret_ref         VARCHAR(256),
    mtls_certificate_fingerprint    VARCHAR(256),
    ip_allowlist                    TEXT,
    allowed_tenants                 TEXT NOT NULL,
    allowed_facilities              TEXT,
    rate_limit_rpm                  INT,
    rate_limit_burst                INT,
    credential_expires_at           TIMESTAMPTZ,
    registered_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at                     TIMESTAMPTZ,
    approved_by                     VARCHAR(64),
    suspended_at                    TIMESTAMPTZ,
    suspended_reason                TEXT,
    revoked_at                      TIMESTAMPTZ,
    tenant_id                       VARCHAR(64)  NOT NULL,
    pod_id                          VARCHAR(64)  NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ih_external_applications_app_code UNIQUE (tenant_id, app_code)
);
CREATE INDEX idx_ih_external_apps_status      ON ih_external_applications (status);
CREATE INDEX idx_ih_external_apps_category    ON ih_external_applications (integration_category);
CREATE INDEX idx_ih_external_apps_tenant      ON ih_external_applications (tenant_id);

-- ─── Integration contracts ──────────────────────────────────────────────────
CREATE TABLE ih_integration_contracts (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    external_app_id                 VARCHAR(36)  NOT NULL REFERENCES ih_external_applications(id) ON DELETE CASCADE,
    contract_code                   VARCHAR(128) NOT NULL,
    version                         VARCHAR(32)  NOT NULL,
    integration_category            VARCHAR(64)  NOT NULL,
    environment                     VARCHAR(16)  NOT NULL,
    effective_from                  TIMESTAMPTZ NOT NULL,
    effective_until                 TIMESTAMPTZ,
    allowed_apis                    TEXT NOT NULL,
    allowed_events                  TEXT,
    allowed_webhook_callbacks       TEXT,
    scopes                          TEXT NOT NULL,
    purpose_of_use                  TEXT NOT NULL,
    consent_basis                   VARCHAR(64),
    signature_method                VARCHAR(32)  NOT NULL,
    rate_limit_rpm                  INT,
    rate_limit_burst                INT,
    error_handling_policy           VARCHAR(32),
    data_retention_days             INT,
    incident_reporting_hours        INT,
    status                          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    signed_by_name                  VARCHAR(256),
    signed_by_email                 VARCHAR(256),
    signed_at                       TIMESTAMPTZ,
    document_ref                    VARCHAR(512),
    tenant_id                       VARCHAR(64)  NOT NULL,
    pod_id                          VARCHAR(64)  NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ih_integration_contracts_code_version UNIQUE (external_app_id, contract_code, version)
);
CREATE INDEX idx_ih_integration_contracts_app ON ih_integration_contracts (external_app_id);
CREATE INDEX idx_ih_integration_contracts_status ON ih_integration_contracts (status);

-- ─── Service-to-service contracts ───────────────────────────────────────────
CREATE TABLE ih_s2s_contracts (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    caller_service_id               VARCHAR(128) NOT NULL,
    callee_service_id               VARCHAR(128) NOT NULL,
    contract_version                VARCHAR(32)  NOT NULL,
    allowed_operations              TEXT NOT NULL,
    allowed_scopes                  TEXT,
    allowed_event_topics            TEXT,
    allowed_request_sources         TEXT NOT NULL,
    rate_limit_rpm                  INT,
    rate_limit_burst                INT,
    owner_team                      VARCHAR(128) NOT NULL,
    support_contact_name            VARCHAR(256),
    support_contact_email           VARCHAR(256),
    last_verified_at                TIMESTAMPTZ,
    status                          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    tenant_id                       VARCHAR(64)  NOT NULL,
    pod_id                          VARCHAR(64)  NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ih_s2s_contracts_pair_version
        UNIQUE (caller_service_id, callee_service_id, contract_version)
);
CREATE INDEX idx_ih_s2s_caller ON ih_s2s_contracts (caller_service_id);
CREATE INDEX idx_ih_s2s_callee ON ih_s2s_contracts (callee_service_id);

-- ─── Webhook subscriptions ──────────────────────────────────────────────────
CREATE TABLE ih_webhook_subscriptions (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    external_app_id                 VARCHAR(36)  NOT NULL REFERENCES ih_external_applications(id) ON DELETE CASCADE,
    integration_contract_id         VARCHAR(36)  NOT NULL REFERENCES ih_integration_contracts(id) ON DELETE CASCADE,
    event_topics                    TEXT NOT NULL,
    delivery_url                    VARCHAR(1024) NOT NULL,
    signature_secret_ref            VARCHAR(256) NOT NULL,
    signature_method                VARCHAR(32)  NOT NULL,
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    filter_facility_ids             TEXT,
    filter_programme_ids            TEXT,
    filter_extra_json               TEXT,
    max_retries                     INT NOT NULL DEFAULT 5,
    initial_delay_ms                INT NOT NULL DEFAULT 1000,
    max_delay_ms                    INT NOT NULL DEFAULT 60000,
    dead_letter_after_retries       INT NOT NULL DEFAULT 10,
    last_delivery_at                TIMESTAMPTZ,
    last_delivery_status            VARCHAR(16),
    consecutive_failures            INT NOT NULL DEFAULT 0,
    tenant_id                       VARCHAR(64) NOT NULL,
    pod_id                          VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_webhook_subs_app    ON ih_webhook_subscriptions (external_app_id);
CREATE INDEX idx_ih_webhook_subs_active ON ih_webhook_subscriptions (active) WHERE active = TRUE;

-- ─── External event subscriptions (per-topic projection) ───────────────────
CREATE TABLE ih_external_event_subscriptions (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    external_app_id                 VARCHAR(36)  NOT NULL REFERENCES ih_external_applications(id) ON DELETE CASCADE,
    integration_contract_id         VARCHAR(36)  NOT NULL REFERENCES ih_integration_contracts(id) ON DELETE CASCADE,
    webhook_subscription_id         VARCHAR(36)  NOT NULL REFERENCES ih_webhook_subscriptions(id) ON DELETE CASCADE,
    event_topic                     VARCHAR(256) NOT NULL,
    status                          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    approved_by                     VARCHAR(64),
    approved_at                     TIMESTAMPTZ,
    tenant_id                       VARCHAR(64) NOT NULL,
    pod_id                          VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_ext_event_subs_topic ON ih_external_event_subscriptions (event_topic);

-- ─── Event catalogue (single canonical list) ────────────────────────────────
CREATE TABLE ih_event_catalogue (
    id                              VARCHAR(36)  NOT NULL PRIMARY KEY,
    topic                           VARCHAR(256) NOT NULL UNIQUE,
    description                     TEXT,
    classification                  VARCHAR(32)  NOT NULL,
    sensitivity_tier                VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    owner_service_id                VARCHAR(128) NOT NULL,
    schema_ref                      VARCHAR(512),
    schema_version                  VARCHAR(32),
    partner_publishable             BOOLEAN NOT NULL DEFAULT FALSE,
    contains_pii                    BOOLEAN NOT NULL DEFAULT FALSE,
    contains_phi                    BOOLEAN NOT NULL DEFAULT FALSE,
    deprecated                      BOOLEAN NOT NULL DEFAULT FALSE,
    deprecated_from                 TIMESTAMPTZ,
    introduced_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ih_event_catalogue_classification ON ih_event_catalogue (classification);
CREATE INDEX idx_ih_event_catalogue_owner          ON ih_event_catalogue (owner_service_id);

-- ─── Seed: canonical externally-publishable events ─────────────────────────
INSERT INTO ih_event_catalogue (id, topic, description, classification, sensitivity_tier, owner_service_id, schema_ref, schema_version, partner_publishable, contains_pii, contains_phi, deprecated)
VALUES
  ('evt-cat-001', 'patient.registered',         'Person registered in client registry.',          'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'vito-service',        'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#PatientReference', '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-002', 'patient.updated',            'Person record updated.',                          'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'vito-service',        'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#PatientReference', '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-003', 'encounter.started',          'Clinical encounter opened.',                      'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'pct-service',         'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#EncounterReference','1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-004', 'encounter.closed',           'Clinical encounter closed.',                      'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'pct-service',         'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#EncounterReference','1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-005', 'lab.order.created',          'A lab order was created.',                        'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'oros-service',        'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#OrderReference',    '1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-006', 'lab.result.received',        'A lab result was received.',                      'EXTERNALLY_PUBLISHABLE', 'ELEVATED', 'oros-service',        'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#OrderReference',    '1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-007', 'imaging.order.created',      'An imaging order was created.',                   'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'pacs-adapter-service','contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#OrderReference',    '1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-008', 'imaging.report.received',    'An imaging report was filed.',                    'EXTERNALLY_PUBLISHABLE', 'ELEVATED', 'pacs-adapter-service','contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#OrderReference',    '1.0', TRUE, FALSE, TRUE,  FALSE),
  ('evt-cat-009', 'stock.level.updated',        'Inventory stock level changed.',                  'EXTERNALLY_PUBLISHABLE', 'LOW',      'inventory-service',   'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#StockReference',    '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-010', 'stock.requisition.created',  'Stock requisition created.',                      'EXTERNALLY_PUBLISHABLE', 'LOW',      'inventory-service',   'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#StockReference',    '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-011', 'payment.initiated',          'Payment intent initiated.',                       'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'mushex-service',      'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#PaymentReference',  '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-012', 'payment.completed',          'Payment confirmed.',                              'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'mushex-service',      'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#PaymentReference',  '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-013', 'claim.submitted',            'Insurance claim submitted.',                      'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'mushex-service',      'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#ClaimReference',    '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-014', 'claim.status.updated',       'Insurance claim status update.',                  'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'mushex-service',      'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#ClaimReference',    '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-015', 'teleconsultation.scheduled', 'Telemedicine session scheduled.',                 'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'scheduling-service',  'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#TelemedicineReference','1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-016', 'teleconsultation.completed', 'Telemedicine session completed.',                 'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'scheduling-service',  'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#TelemedicineReference','1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-017', 'consent.granted',            'Consent granted by data subject.',                'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'tshepo-consent-service','contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#ConsentReference','1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-018', 'consent.revoked',            'Consent revoked by data subject.',                'EXTERNALLY_PUBLISHABLE', 'STANDARD', 'tshepo-consent-service','contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#ConsentReference','1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-019', 'provider.verified',          'Provider record verified.',                       'EXTERNALLY_PUBLISHABLE', 'LOW',      'varapi-service',      'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#ProviderReference', '1.0', TRUE, FALSE, FALSE, FALSE),
  ('evt-cat-020', 'facility.updated',           'Facility registry update.',                       'EXTERNALLY_PUBLISHABLE', 'LOW',      'tuso-service',        'contracts/asyncapi/health-os-external-publishable-events.asyncapi.yaml#FacilityReference', '1.0', TRUE, FALSE, FALSE, FALSE);

-- A small sample of internal-only events so the catalogue distinguishes both tiers.
INSERT INTO ih_event_catalogue (id, topic, description, classification, sensitivity_tier, owner_service_id, schema_ref, schema_version, partner_publishable, contains_pii, contains_phi, deprecated)
VALUES
  ('evt-cat-101', 'tshepo.policy.evaluated',    'PDP decision recorded.',                          'INTERNAL_ONLY', 'STANDARD', 'tshepo-authz-service', NULL, '1.0', FALSE, FALSE, FALSE, FALSE),
  ('evt-cat-102', 'audit.event.recorded',       'Audit-ledger event recorded.',                    'INTERNAL_ONLY', 'STANDARD', 'audit-ledger-service', NULL, '1.0', FALSE, FALSE, FALSE, FALSE),
  ('evt-cat-103', 'integration.route.dispatched','Integration hub dispatched a route.',            'INTERNAL_ONLY', 'LOW',      'integration-hub',      NULL, '1.0', FALSE, FALSE, FALSE, FALSE),
  ('evt-cat-104', 'integration.deadletter.created','Dead-letter row created.',                     'INTERNAL_ONLY', 'STANDARD', 'integration-hub',      NULL, '1.0', FALSE, FALSE, FALSE, FALSE),
  ('evt-cat-105', 'nompilo.skill.invoked',      'AI skill invocation recorded.',                   'INTERNAL_ONLY', 'STANDARD', 'guidance-service',     NULL, '1.0', FALSE, FALSE, FALSE, FALSE);
