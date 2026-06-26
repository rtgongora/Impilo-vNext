-- ============================================================================
-- indawo-service V007 — Public-health surveillance, outbreak, case
-- investigation and field-team coordination (net-new, L2 / T4 build).
-- Indawo is the SoR for public-health PLACE intelligence. Tenant-scoped.
-- ============================================================================

-- ── Surveillance alerts (signals detected for a place / area) ───────────────
CREATE TABLE ind_surveillance_alerts (
    id               BIGSERIAL PRIMARY KEY,
    alert_id         UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    site_id          UUID,                      -- optional originating place
    signal_type      VARCHAR(64) NOT NULL,      -- e.g. SYNDROMIC, LAB_CONFIRMED, RUMOUR, ENV
    condition_code   VARCHAR(64),               -- disease/condition code
    severity         VARCHAR(16) NOT NULL DEFAULT 'MEDIUM', -- LOW|MEDIUM|HIGH|CRITICAL
    status           VARCHAR(32) NOT NULL DEFAULT 'OPEN',   -- OPEN|TRIAGED|LINKED|CLOSED
    case_count       INTEGER NOT NULL DEFAULT 0,
    description      TEXT,
    detected_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT uq_ind_surveillance_alert UNIQUE (alert_id)
);
CREATE INDEX idx_ind_surv_alert_tenant ON ind_surveillance_alerts (tenant_id);
CREATE INDEX idx_ind_surv_alert_status ON ind_surveillance_alerts (tenant_id, status);

-- ── Outbreaks (declared events spanning places / areas) ─────────────────────
CREATE TABLE ind_outbreaks (
    id               BIGSERIAL PRIMARY KEY,
    outbreak_id      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    condition_code   VARCHAR(64) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    severity         VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status           VARCHAR(32) NOT NULL DEFAULT 'SUSPECTED', -- SUSPECTED|CONFIRMED|CONTAINED|CLOSED
    area_description VARCHAR(512),
    primary_site_id  UUID,
    declared_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    case_count       INTEGER NOT NULL DEFAULT 0,
    death_count      INTEGER NOT NULL DEFAULT 0,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT uq_ind_outbreak UNIQUE (outbreak_id)
);
CREATE INDEX idx_ind_outbreak_tenant ON ind_outbreaks (tenant_id);
CREATE INDEX idx_ind_outbreak_status ON ind_outbreaks (tenant_id, status);

-- ── Case investigations (line-list entries tied to an outbreak/alert) ───────
CREATE TABLE ind_case_investigations (
    id               BIGSERIAL PRIMARY KEY,
    case_id          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    outbreak_id      UUID,                      -- nullable: standalone investigation
    alert_id         UUID,
    site_id          UUID,
    cpid             VARCHAR(128),              -- pseudonymous person ref (no PII)
    classification   VARCHAR(32) NOT NULL DEFAULT 'SUSPECTED', -- SUSPECTED|PROBABLE|CONFIRMED|DISCARDED
    status           VARCHAR(32) NOT NULL DEFAULT 'OPEN',      -- OPEN|IN_PROGRESS|RESOLVED
    onset_date       DATE,
    investigated_by  VARCHAR(255),
    findings         TEXT,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT uq_ind_case UNIQUE (case_id)
);
CREATE INDEX idx_ind_case_tenant ON ind_case_investigations (tenant_id);
CREATE INDEX idx_ind_case_outbreak ON ind_case_investigations (outbreak_id);

-- ── Field teams (inspector / rapid-response team coordination) ──────────────
CREATE TABLE ind_field_teams (
    id               BIGSERIAL PRIMARY KEY,
    team_id          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    team_type        VARCHAR(64) NOT NULL DEFAULT 'RAPID_RESPONSE', -- RAPID_RESPONSE|INSPECTION|SURVEILLANCE
    status           VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',      -- AVAILABLE|DEPLOYED|STANDDOWN
    lead_health_id   VARCHAR(128),
    member_count     INTEGER NOT NULL DEFAULT 0,
    base_site_id     UUID,
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT uq_ind_field_team UNIQUE (team_id)
);
CREATE INDEX idx_ind_team_tenant ON ind_field_teams (tenant_id);

-- ── Field-team deployments (team -> outbreak/site assignment) ───────────────
CREATE TABLE ind_field_team_deployments (
    id               BIGSERIAL PRIMARY KEY,
    deployment_id    UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    team_id          UUID NOT NULL,
    outbreak_id      UUID,
    site_id          UUID,
    status           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',         -- ACTIVE|COMPLETED|RECALLED
    deployed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recalled_at      TIMESTAMPTZ,
    objective        VARCHAR(512),
    metadata         JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    CONSTRAINT uq_ind_deployment UNIQUE (deployment_id)
);
CREATE INDEX idx_ind_deploy_tenant ON ind_field_team_deployments (tenant_id);
CREATE INDEX idx_ind_deploy_team ON ind_field_team_deployments (team_id);
