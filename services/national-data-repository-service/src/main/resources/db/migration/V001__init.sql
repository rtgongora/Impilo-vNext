-- ============================================================
-- NDR — National Data Repository  (V001 initial schema)
-- Analytics-ready store for national datasets, versioned
-- table definitions, materialized views, and access policies.
-- ============================================================

-- 1. Datasets — top-level analytics dataset registry
CREATE TABLE ndr_datasets (
    dataset_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    dataset_key     VARCHAR(128)    NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    description     TEXT,
    owner           VARCHAR(128),
    status          VARCHAR(40)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_ndr_datasets_tenant_key UNIQUE (tenant_id, dataset_key)
);

CREATE INDEX idx_ndr_datasets_tenant ON ndr_datasets (tenant_id);
CREATE INDEX idx_ndr_datasets_tenant_status ON ndr_datasets (tenant_id, status);

-- 2. Dataset versions — immutable snapshots of a dataset
CREATE TABLE ndr_dataset_versions (
    version_id      VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    dataset_id      VARCHAR(26)     NOT NULL REFERENCES ndr_datasets(dataset_id),
    version_number  INTEGER         NOT NULL,
    schema_json     JSONB           NOT NULL DEFAULT '{}',
    row_count       BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(40)     NOT NULL DEFAULT 'DRAFT',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_ndr_versions_dataset_number UNIQUE (dataset_id, version_number)
);

CREATE INDEX idx_ndr_versions_tenant ON ndr_dataset_versions (tenant_id);
CREATE INDEX idx_ndr_versions_dataset ON ndr_dataset_versions (dataset_id);

-- 3. Dataset access policies — stub for future RBAC on datasets
CREATE TABLE ndr_dataset_access_policies (
    policy_id       VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    dataset_id      VARCHAR(26)     NOT NULL REFERENCES ndr_datasets(dataset_id),
    principal_type  VARCHAR(40)     NOT NULL DEFAULT 'ROLE',
    principal_id    VARCHAR(128)    NOT NULL,
    permission      VARCHAR(40)     NOT NULL DEFAULT 'READ',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ndr_policies_tenant_dataset ON ndr_dataset_access_policies (tenant_id, dataset_id);

-- 4. Materialized views — pre-computed query results (stub)
CREATE TABLE ndr_materialized_views (
    view_id         VARCHAR(26)     PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    dataset_key     VARCHAR(128)    NOT NULL,
    view_name       VARCHAR(256)    NOT NULL,
    row_data        JSONB           NOT NULL DEFAULT '{}',
    refreshed_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ndr_matviews_tenant_key ON ndr_materialized_views (tenant_id, dataset_key);

-- Seed stub rows for query testing
INSERT INTO ndr_materialized_views (view_id, tenant_id, dataset_key, view_name, row_data)
VALUES
    ('01STUB0000000000000000001', '00000000-0000-0000-0000-000000000001', 'facility_summary', 'Facility Summary',
     '{"facility_count": 142, "province": "Harare", "period": "2025-Q4"}'),
    ('01STUB0000000000000000002', '00000000-0000-0000-0000-000000000001', 'facility_summary', 'Facility Summary',
     '{"facility_count": 87, "province": "Bulawayo", "period": "2025-Q4"}'),
    ('01STUB0000000000000000003', '00000000-0000-0000-0000-000000000001', 'patient_volume', 'Patient Volume',
     '{"total_visits": 23400, "facility_id": "fac-001", "period": "2025-Q4"}');

-- 5. Event outbox — transactional outbox for Kafka publishing
CREATE TABLE ndr_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_ndr_outbox_unpublished ON ndr_event_outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_ndr_outbox_tenant_ts ON ndr_event_outbox (tenant_id, created_at);

-- 6. Idempotency keys — deduplication for v1.1 command endpoints
CREATE TABLE idempotency_keys (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(128)    NOT NULL,
    pod_id          VARCHAR(128)    NOT NULL,
    idempotency_key VARCHAR(256)    NOT NULL,
    request_hash    VARCHAR(64)     NOT NULL,
    response_status INTEGER         NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_tenant_pod_key UNIQUE (tenant_id, pod_id, idempotency_key)
);

CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
