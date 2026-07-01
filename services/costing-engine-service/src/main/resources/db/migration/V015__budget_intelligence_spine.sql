-- Intelligent Budgeting & Budget Tracking — foundation spine (Wave 0)
-- Extends COSTA (owner of operational budgeting). Wraps, does not replace,
-- the existing costa_budget_allocations projection (V004). New rich model:
-- budget header -> version -> line, with funding sources and an approval/FSM log.
-- Amounts NUMERIC(18,2) for national-scope headroom. Every row tenant-scoped.

-- Funding source / donor / grant reference -----------------------------------
CREATE TABLE IF NOT EXISTS costa_funding_source (
    id                 BIGSERIAL PRIMARY KEY,
    funding_source_id  UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id          UUID NOT NULL,
    name               VARCHAR(256) NOT NULL,
    code               VARCHAR(64)  NOT NULL,
    source_type        VARCHAR(16)  NOT NULL,
    donor_name         VARCHAR(256),
    grant_reference    VARCHAR(128),
    ceiling_amount     NUMERIC(18,2),
    absorbed_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    start_date         DATE,
    end_date           DATE,
    status             VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_funding_source_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_costa_funding_source_type
        CHECK (source_type IN ('GOVERNMENT','DONOR','INSURER','INTERNAL','OOP'))
);
CREATE INDEX idx_costa_funding_source_tenant ON costa_funding_source (tenant_id, status);

-- Budget header --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS costa_budget (
    id                 BIGSERIAL PRIMARY KEY,
    budget_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id          UUID NOT NULL,
    facility_id        UUID,
    scope_level        VARCHAR(16) NOT NULL DEFAULT 'FACILITY',
    period_year        INT NOT NULL,
    period_start       DATE,
    period_end         DATE,
    title              VARCHAR(256) NOT NULL,
    grant_id           VARCHAR(128),
    programme_code     VARCHAR(64),
    funding_source_id  UUID REFERENCES costa_funding_source(funding_source_id),
    currency           VARCHAR(8) NOT NULL DEFAULT 'USD',
    status             VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_version_id UUID,
    notes              TEXT,
    created_by         VARCHAR(128),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_budget_scope
        CHECK (scope_level IN ('FACILITY','DISTRICT','PROVINCE','NATIONAL')),
    CONSTRAINT chk_costa_budget_status
        CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','ACTIVE',
                          'REVISED','FROZEN','CLOSED','ARCHIVED'))
);
CREATE INDEX idx_costa_budget_scope ON costa_budget (tenant_id, facility_id, period_year, status);

-- Budget version (baseline / revision / reprogramming) -----------------------
CREATE TABLE IF NOT EXISTS costa_budget_version (
    id                        BIGSERIAL PRIMARY KEY,
    version_id                UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id                 UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id                 UUID NOT NULL,
    version_no                INT NOT NULL,
    is_current                BOOLEAN NOT NULL DEFAULT FALSE,
    basis                     VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL',
    effective_from            DATE,
    total_amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    status                    VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    superseded_by_version_id  UUID,
    created_by                VARCHAR(128),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_costa_budget_version_no UNIQUE (budget_id, version_no),
    CONSTRAINT chk_costa_budget_version_basis
        CHECK (basis IN ('ORIGINAL','REVISION','REPROGRAMMING'))
);
CREATE INDEX idx_costa_budget_version_budget ON costa_budget_version (tenant_id, budget_id, version_no);

-- Budget line ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS costa_budget_line (
    id                 BIGSERIAL PRIMARY KEY,
    line_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    version_id         UUID NOT NULL REFERENCES costa_budget_version(version_id),
    budget_id          UUID NOT NULL REFERENCES costa_budget(budget_id),
    tenant_id          UUID NOT NULL,
    cost_center_id     UUID,
    department_id      VARCHAR(128),
    budget_category    VARCHAR(64) NOT NULL,
    funding_source_id  UUID REFERENCES costa_funding_source(funding_source_id),
    programme_code     VARCHAR(64),
    grant_id           VARCHAR(128),
    district_code      VARCHAR(64),
    gl_account_ref     VARCHAR(128),
    allocated_amount   NUMERIC(18,2) NOT NULL DEFAULT 0,
    allocation_id      UUID,
    line_order         INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_costa_budget_line_budget ON costa_budget_line (tenant_id, budget_id, budget_category);
CREATE INDEX idx_costa_budget_line_version ON costa_budget_line (tenant_id, version_id);
CREATE INDEX idx_costa_budget_line_funding ON costa_budget_line (tenant_id, funding_source_id);

-- Approval / lifecycle transition log (the audit trail) ----------------------
CREATE TABLE IF NOT EXISTS costa_budget_approval (
    id               BIGSERIAL PRIMARY KEY,
    approval_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    budget_id        UUID NOT NULL REFERENCES costa_budget(budget_id),
    version_id       UUID,
    tenant_id        UUID NOT NULL,
    step_no          INT NOT NULL DEFAULT 0,
    action           VARCHAR(16) NOT NULL,
    decided_by       VARCHAR(128),
    decided_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decision_reason  TEXT,
    from_status      VARCHAR(20),
    to_status        VARCHAR(20),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_costa_budget_approval_action
        CHECK (action IN ('SUBMIT','REVIEW','APPROVE','REJECT','RETURN',
                          'ACTIVATE','FREEZE','CLOSE','ARCHIVE'))
);
CREATE INDEX idx_costa_budget_approval_budget ON costa_budget_approval (tenant_id, budget_id, decided_at DESC);
