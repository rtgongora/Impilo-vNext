CREATE TABLE IF NOT EXISTS surv.intelligence_rule_condition (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id BIGINT NOT NULL REFERENCES surv.ph_alert_rules(id) ON DELETE CASCADE,
    metric_key TEXT NOT NULL,
    comparison_operator TEXT NOT NULL DEFAULT 'GT',
    threshold_value DOUBLE PRECISION NOT NULL DEFAULT 0,
    scope_ref TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intelligence_rule_condition_tenant ON surv.intelligence_rule_condition(tenant_id);
CREATE INDEX IF NOT EXISTS idx_intelligence_rule_condition_rule ON surv.intelligence_rule_condition(rule_id);

CREATE TABLE IF NOT EXISTS surv.intelligence_rule_evaluation (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id BIGINT NOT NULL REFERENCES surv.ph_alert_rules(id) ON DELETE CASCADE,
    evaluation_mode TEXT NOT NULL,
    evaluation_status TEXT NOT NULL,
    triggered BOOLEAN NOT NULL DEFAULT FALSE,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intelligence_rule_evaluation_tenant ON surv.intelligence_rule_evaluation(tenant_id);

CREATE TABLE IF NOT EXISTS surv.intelligence_trigger_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id BIGINT NOT NULL REFERENCES surv.ph_alert_rules(id) ON DELETE CASCADE,
    evaluation_id BIGINT REFERENCES surv.intelligence_rule_evaluation(id) ON DELETE SET NULL,
    dedup_key TEXT,
    status TEXT NOT NULL,
    trigger_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intelligence_trigger_history_tenant ON surv.intelligence_trigger_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_intelligence_trigger_history_dedup ON surv.intelligence_trigger_history(tenant_id, dedup_key);

CREATE TABLE IF NOT EXISTS surv.intelligence_alert (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_id BIGINT NOT NULL REFERENCES surv.ph_alert_rules(id) ON DELETE CASCADE,
    trigger_history_id BIGINT REFERENCES surv.intelligence_trigger_history(id) ON DELETE SET NULL,
    severity TEXT NOT NULL DEFAULT 'MODERATE',
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN',
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intelligence_alert_tenant ON surv.intelligence_alert(tenant_id);
CREATE INDEX IF NOT EXISTS idx_intelligence_alert_status ON surv.intelligence_alert(tenant_id, status);

CREATE TABLE IF NOT EXISTS surv.intelligence_generated_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alert_id BIGINT NOT NULL REFERENCES surv.intelligence_alert(id) ON DELETE CASCADE,
    task_ref TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intelligence_generated_task_tenant ON surv.intelligence_generated_task(tenant_id);

CREATE TABLE IF NOT EXISTS surv.inspection_schedule (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    site_id UUID NOT NULL,
    inspection_type TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    responsible_ref TEXT,
    priority TEXT NOT NULL DEFAULT 'MEDIUM',
    reason TEXT,
    scope_ref TEXT,
    recurrence_rule TEXT,
    notes TEXT,
    notification_preference TEXT,
    status TEXT NOT NULL DEFAULT 'SCHEDULED',
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_inspection_schedule_tenant ON surv.inspection_schedule(tenant_id);
