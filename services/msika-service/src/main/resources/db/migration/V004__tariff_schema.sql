CREATE TABLE msika_tariffs (
    tariff_id           VARCHAR(26)     PRIMARY KEY,
    tariff_code         VARCHAR(100)    NOT NULL UNIQUE,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    unit_price          NUMERIC(18,4)   NOT NULL DEFAULT 0,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'USD',
    billing_model       VARCHAR(50)     NOT NULL DEFAULT 'FEE_FOR_SERVICE',
    effective_from      DATE            NOT NULL,
    effective_to        DATE,
    tenant_id           UUID,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    pricing_rules       JSONB           NOT NULL DEFAULT '{}',
    created_by          VARCHAR(128)    NOT NULL,
    updated_by          VARCHAR(128),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_tariff_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_tariff_billing_model CHECK (billing_model IN (
        'FEE_FOR_SERVICE', 'CAPITATION', 'DRG', 'PACKAGE', 'COST_PLUS'
    ))
);

CREATE INDEX idx_msika_tariffs_code ON msika_tariffs (tariff_code);
CREATE INDEX idx_msika_tariffs_status ON msika_tariffs (status);
CREATE INDEX idx_msika_tariffs_tenant ON msika_tariffs (tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_msika_tariffs_effective ON msika_tariffs (effective_from, effective_to);
