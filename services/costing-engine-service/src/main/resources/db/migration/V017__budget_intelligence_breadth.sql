-- Intelligent Budgeting — breadth (Wave 2): revisions/reprogramming,
-- transparent recommendations, thresholds, reconciliation exceptions, assumptions.

-- Budget assumptions (drivers feeding transparent forecasting) ----------------
CREATE TABLE IF NOT EXISTS costa_budget_assumption (
    id            BIGSERIAL PRIMARY KEY,
    assumption_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    version_id    UUID NOT NULL REFERENCES costa_budget_version(version_id),
    budget_id     UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id     UUID NOT NULL,
    driver_key    VARCHAR(64) NOT NULL,
    driver_value  VARCHAR(128),
    unit          VARCHAR(32),
    rationale     TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_budget_assumption ON costa_budget_assumption (tenant_id, version_id);

-- Budget revision / reprogramming --------------------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_revision (
    id               BIGSERIAL PRIMARY KEY,
    revision_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id        UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id        UUID NOT NULL,
    from_version_id  UUID,
    to_version_id    UUID,
    revision_type    VARCHAR(20) NOT NULL,
    reason           TEXT,
    net_change       NUMERIC(18,2) NOT NULL DEFAULT 0,
    requested_by     VARCHAR(128),
    approved_by      VARCHAR(128),
    status           VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_revision_type
        CHECK (revision_type IN ('VIREMENT','SUPPLEMENTARY','REPROGRAMMING','RESCISSION')),
    CONSTRAINT chk_costa_revision_status
        CHECK (status IN ('REQUESTED','APPROVED','REJECTED','APPLIED'))
);
CREATE INDEX idx_costa_revision_budget ON costa_budget_revision (tenant_id, budget_id, created_at DESC);

-- Thresholds (drive availability control + alerts) ---------------------------
CREATE TABLE IF NOT EXISTS costa_budget_threshold (
    id                      BIGSERIAL PRIMARY KEY,
    threshold_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id               UUID NOT NULL,
    budget_id               UUID,
    budget_line_id          UUID,
    metric                  VARCHAR(20) NOT NULL,
    warn_at                 NUMERIC(18,4) NOT NULL,
    hard_stop_at            NUMERIC(18,4),
    allow_emergency_override BOOLEAN NOT NULL DEFAULT TRUE,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_threshold_metric
        CHECK (metric IN ('UTILISATION_PCT','BURN_RATE','AVAILABLE_ABS'))
);
CREATE INDEX idx_costa_threshold_budget ON costa_budget_threshold (tenant_id, budget_id, active);

-- Recommendations (rule-based, explainable) ----------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_recommendation (
    id                BIGSERIAL PRIMARY KEY,
    recommendation_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id         UUID NOT NULL REFERENCES costa_budget(budget_id),
    budget_line_id    UUID,
    tenant_id         UUID NOT NULL,
    rule_code         VARCHAR(32) NOT NULL,
    severity          VARCHAR(8) NOT NULL,
    title             VARCHAR(256) NOT NULL,
    rationale         TEXT,
    evidence_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    suggested_action  VARCHAR(256),
    status            VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_reco_severity CHECK (severity IN ('INFO','WARN','CRITICAL')),
    CONSTRAINT chk_costa_reco_status CHECK (status IN ('OPEN','ACKED','DISMISSED','ACTIONED'))
);
CREATE INDEX idx_costa_reco_budget ON costa_budget_recommendation (tenant_id, budget_id, status);

-- Reconciliation exceptions --------------------------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_reconciliation_exception (
    id               BIGSERIAL PRIMARY KEY,
    exception_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id        UUID NOT NULL,
    budget_id        UUID,
    budget_line_id   UUID,
    exception_type   VARCHAR(28) NOT NULL,
    source           VARCHAR(20),
    source_reference VARCHAR(160),
    expected         NUMERIC(18,2),
    observed         NUMERIC(18,2),
    delta            NUMERIC(18,2),
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    resolution_note  TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_recon_type
        CHECK (exception_type IN ('UNMATCHED_ACTUAL','COMMITMENT_WITHOUT_ACTUAL','OVER_LIQUIDATION',
                                  'DUPLICATE_REFERENCE','SOURCE_MISMATCH')),
    CONSTRAINT chk_costa_recon_status
        CHECK (status IN ('OPEN','INVESTIGATING','RESOLVED','WRITTEN_OFF'))
);
CREATE INDEX idx_costa_recon_status ON costa_budget_reconciliation_exception (tenant_id, status, exception_type);
