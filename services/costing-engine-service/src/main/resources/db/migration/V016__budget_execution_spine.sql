-- Intelligent Budgeting — execution spine (Wave 1: commitments, actuals,
-- variance snapshots, forecast snapshots). Commitments and actuals are
-- REFERENCE LEDGERS: truth stays with the owner (Oros/Msika/Dura/MusheX/COSTA
-- invoices/ERP); COSTA stores the pointer + amount, never re-derives it.

-- Commitments (obligation before payment) ------------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_commitment (
    id                  BIGSERIAL PRIMARY KEY,
    commitment_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_line_id      UUID NOT NULL REFERENCES costa_budget_line(line_id),
    budget_id           UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id           UUID NOT NULL,
    owner_system        VARCHAR(16) NOT NULL,
    owner_reference     VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    committed_amount    NUMERIC(18,2) NOT NULL,
    obligated_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    liquidated_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    status              VARCHAR(24) NOT NULL DEFAULT 'COMMITTED',
    committed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_budget_commitment_owner
        CHECK (owner_system IN ('OROS','MSIKA','DURA','FUNDO','DAIDZAI','PCT','COSTA','MANUAL')),
    CONSTRAINT chk_costa_budget_commitment_status
        CHECK (status IN ('PROPOSED','COMMITTED','PARTIALLY_LIQUIDATED','LIQUIDATED','CANCELLED','EXPIRED'))
);
CREATE INDEX idx_costa_budget_commitment_line ON costa_budget_commitment (tenant_id, budget_line_id, status);
CREATE INDEX idx_costa_budget_commitment_owner ON costa_budget_commitment (tenant_id, owner_system, owner_reference);

-- Actual expenditure reference (idempotent) ----------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_actual_reference (
    id                  BIGSERIAL PRIMARY KEY,
    actual_ref_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_line_id      UUID NOT NULL REFERENCES costa_budget_line(line_id),
    budget_id           UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id           UUID NOT NULL,
    source              VARCHAR(20) NOT NULL,
    source_reference    VARCHAR(160) NOT NULL,
    commitment_id       UUID REFERENCES costa_budget_commitment(commitment_id),
    actual_amount       NUMERIC(18,2) NOT NULL,
    ingest_source       VARCHAR(12) NOT NULL DEFAULT 'API',
    is_reversed         BOOLEAN NOT NULL DEFAULT FALSE,
    reversal_of         UUID,
    posted_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_budget_actual_ref UNIQUE (tenant_id, source, source_reference),
    CONSTRAINT chk_costa_budget_actual_source
        CHECK (source IN ('MUSHEX_PAYMENT','COSTA_INVOICE','EXTERNAL_ERP')),
    CONSTRAINT chk_costa_budget_actual_ingest
        CHECK (ingest_source IN ('EVENT','API','IMPORT'))
);
CREATE INDEX idx_costa_budget_actual_line ON costa_budget_actual_reference (tenant_id, budget_line_id, posted_at);

-- Variance snapshot (immutable, explainable) ---------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_variance_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    variance_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id           UUID NOT NULL REFERENCES costa_budget(budget_id),
    budget_line_id      UUID,
    tenant_id           UUID NOT NULL,
    as_of_date          DATE NOT NULL,
    period_scope        VARCHAR(8) NOT NULL DEFAULT 'YTD',
    budgeted            NUMERIC(18,2) NOT NULL DEFAULT 0,
    committed           NUMERIC(18,2) NOT NULL DEFAULT 0,
    actual              NUMERIC(18,2) NOT NULL DEFAULT 0,
    available           NUMERIC(18,2) NOT NULL DEFAULT 0,
    variance_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    variance_pct        NUMERIC(9,4),
    classification      VARCHAR(24) NOT NULL,
    driver_note         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_variance_scope CHECK (period_scope IN ('MTD','QTD','YTD'))
);
CREATE INDEX idx_costa_variance_budget ON costa_budget_variance_snapshot (tenant_id, budget_id, as_of_date DESC);

-- Forecast snapshot (transparent, drivers exposed) ---------------------------
CREATE TABLE IF NOT EXISTS costa_budget_forecast_snapshot (
    id                    BIGSERIAL PRIMARY KEY,
    forecast_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id             UUID NOT NULL REFERENCES costa_budget(budget_id),
    budget_line_id        UUID,
    tenant_id             UUID NOT NULL,
    as_of_date            DATE NOT NULL,
    method                VARCHAR(16) NOT NULL,
    elapsed_fraction      NUMERIC(9,6),
    burn_rate             NUMERIC(18,4),
    projected_year_end    NUMERIC(18,2),
    projected_overrun     NUMERIC(18,2),
    confidence_note       TEXT,
    inputs_json           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_forecast_method CHECK (method IN ('LINEAR_YTD','BURN_RATE','SEASONAL_NAIVE'))
);
CREATE INDEX idx_costa_forecast_budget ON costa_budget_forecast_snapshot (tenant_id, budget_id, as_of_date DESC);
