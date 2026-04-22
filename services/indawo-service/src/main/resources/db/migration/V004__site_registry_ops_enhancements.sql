-- ============================================================================
-- indawo-service V004 — Site Registry Ops Enhancements
-- - Inspection scoring columns
-- - Site assignment/routing foundation
-- ============================================================================

-- Inspection scoring (stored summary; detailed scoring can be recomputed from findings)
ALTER TABLE ind_site_inspections
    ADD COLUMN IF NOT EXISTS score_total          NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS score_max            NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS score_percent        NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS critical_fail_count  INT,
    ADD COLUMN IF NOT EXISTS major_fail_count     INT,
    ADD COLUMN IF NOT EXISTS minor_fail_count     INT;

CREATE INDEX IF NOT EXISTS idx_ind_inspections_status ON ind_site_inspections (tenant_id, status);

-- Assignment / routing foundation (jurisdiction + workload)
CREATE TABLE IF NOT EXISTS ind_site_assignments (
    assignment_id   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    site_id         UUID        NOT NULL REFERENCES ind_sites(site_id),
    assignment_type VARCHAR(64) NOT NULL, -- INSPECTION, COMPLIANCE, REVIEW, LICENSING
    assigned_role   VARCHAR(64),          -- e.g. INSPECTOR, ENV_HEALTH, LICENSING_OFFICER
    assigned_to_ref VARCHAR(255),         -- provider/user ref
    jurisdiction_ref VARCHAR(255),
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    due_date        DATE,
    notes           TEXT,
    created_by      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ind_assignments_site ON ind_site_assignments (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_ind_assignments_status ON ind_site_assignments (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_ind_assignments_assignee ON ind_site_assignments (tenant_id, assigned_to_ref) WHERE assigned_to_ref IS NOT NULL;

