-- Wave 21 — public-health field tasks (BFF /internal/v1/public-health/field-tasks proxy target).

CREATE TABLE IF NOT EXISTS surv.field_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    title           VARCHAR(500) NOT NULL,
    task_type       VARCHAR(100) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    assigned_to     VARCHAR(255),
    created_by      VARCHAR(255),
    due_date        DATE,
    details         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_field_tasks_tenant
    ON surv.field_tasks (tenant_id, created_at DESC);
