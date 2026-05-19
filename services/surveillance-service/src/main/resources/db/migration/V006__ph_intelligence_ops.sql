CREATE TABLE IF NOT EXISTS surv.ph_alert_rules (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_key TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    threshold_value DOUBLE PRECISION NOT NULL DEFAULT 0,
    comparison_operator TEXT NOT NULL DEFAULT 'GT',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMPTZ,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ph_alert_rules_tenant ON surv.ph_alert_rules(tenant_id);

CREATE TABLE IF NOT EXISTS surv.ph_data_source_statuses (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_key TEXT NOT NULL,
    source_name TEXT NOT NULL,
    health_status TEXT NOT NULL DEFAULT 'HEALTHY',
    last_sync_at TIMESTAMPTZ,
    latency_seconds BIGINT NOT NULL DEFAULT 0,
    error_count_24h INT NOT NULL DEFAULT 0,
    details TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ph_data_source_statuses_tenant ON surv.ph_data_source_statuses(tenant_id);

CREATE TABLE IF NOT EXISTS surv.ph_data_quality_issues (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    issue_type TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'MEDIUM',
    title TEXT NOT NULL,
    description TEXT,
    scope_ref TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ph_data_quality_issues_tenant ON surv.ph_data_quality_issues(tenant_id);
