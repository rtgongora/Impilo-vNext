-- ============================================================================
-- data-governance-service V008 — De-identification pipeline (Identity Contract §15)
--
-- Ratified design: docs/data-platform/de-identification-pipeline.md
--
-- The operational SHR is pseudonymised (CPID-keyed), not anonymised. Analytics
-- and research datasets are produced ONLY by this pipeline, which moves rows
-- through four zones:
--
--   RAW         — CPID-keyed source rows; never a materialisation target
--   TOKENISED   — CPID replaced by per-dataset HMAC subject token,
--                 direct identifiers stripped (allowlist, fail-closed)
--   GENERALISED — quasi-identifiers reduced (DOB→age band, geo→district,
--                 dates→month/shift, rare categoricals→OTHER)
--   RELEASED    — k-anonymity verified/suppressed, data-use policy gate passed
--
-- Custody note: deid_dataset.secret_ref is a REFERENCE into tshepo-keys
-- custody. The HMAC key material itself is NEVER stored in this schema.
-- ============================================================================

-- Data-use release policy (deny-by-default gate: no ACTIVE policy => no release)
CREATE TABLE deid_release_policy (
    policy_id               UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    policy_code             VARCHAR(128)    NOT NULL,
    purpose                 VARCHAR(64)     NOT NULL,   -- RESEARCH | PUBLIC_HEALTH
    requester               VARCHAR(255),
    retention_days          INT,
    reid_prohibited         BOOLEAN         NOT NULL DEFAULT TRUE,
    k_threshold             INT             NOT NULL DEFAULT 5,
    quasi_identifier_rules  JSONB,                      -- {columns:{col:RULE}, quasiColumns:[...], rareThreshold, maxDateShiftDays}
    allowlist_columns       JSONB,                      -- ["gender","district",...] — anything else is dropped
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | SUSPENDED | REVOKED
    version                 INT             NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deid_policy_code UNIQUE (tenant_id, policy_code)
);

CREATE INDEX idx_deid_policy_purpose ON deid_release_policy (tenant_id, purpose, status);

-- Registered de-identification datasets
CREATE TABLE deid_dataset (
    dataset_id      UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    dataset_code    VARCHAR(64)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    purpose         VARCHAR(64)     NOT NULL,   -- RESEARCH | PUBLIC_HEALTH
    zone_target     VARCHAR(16)     NOT NULL DEFAULT 'RELEASED',  -- TOKENISED | GENERALISED | RELEASED (never RAW)
    policy_ref      VARCHAR(128),               -- deid_release_policy.policy_code
    secret_ref      VARCHAR(255)    NOT NULL,   -- key REFERENCE (tshepo-keys custody); never key material
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE | SUSPENDED | RETIRED
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_deid_dataset_code UNIQUE (tenant_id, dataset_code)
);

-- De-identification run ledger (FSM: REQUESTED → TRANSFORMING → QA → RELEASED | REJECTED)
CREATE TABLE deid_run (
    run_id              UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dataset_id          UUID            NOT NULL REFERENCES deid_dataset (dataset_id),
    status              VARCHAR(32)     NOT NULL DEFAULT 'REQUESTED',
    rows_in             INT             NOT NULL DEFAULT 0,
    rows_out            INT             NOT NULL DEFAULT 0,
    rows_suppressed     INT             NOT NULL DEFAULT 0,
    cells_suppressed    INT             NOT NULL DEFAULT 0,
    rejection_reason    TEXT,
    qa_report_json      JSONB,
    policy_id           UUID,
    policy_version      VARCHAR(64),
    requested_by        VARCHAR(255)    NOT NULL,
    correlation_id      VARCHAR(255),
    requested_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deid_run_dataset ON deid_run (tenant_id, dataset_id, requested_at DESC);
CREATE INDEX idx_deid_run_status  ON deid_run (tenant_id, status);

-- Release manifest — written exactly once per RELEASED run; downstream (NDR /
-- data-warehouse) materialises against this manifest + the released outbox event.
CREATE TABLE deid_release_manifest (
    manifest_id             UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    run_id                  UUID            NOT NULL REFERENCES deid_run (run_id),
    dataset_id              UUID            NOT NULL REFERENCES deid_dataset (dataset_id),
    dataset_code            VARCHAR(64)     NOT NULL,
    zone_target             VARCHAR(16)     NOT NULL,
    row_count               INT             NOT NULL,
    k_threshold             INT             NOT NULL,
    policy_id               UUID            NOT NULL,
    policy_version          VARCHAR(64),
    columns_json            JSONB,          -- released column names (no values)
    suppression_stats_json  JSONB,
    released_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_deid_manifest_run ON deid_release_manifest (run_id);
