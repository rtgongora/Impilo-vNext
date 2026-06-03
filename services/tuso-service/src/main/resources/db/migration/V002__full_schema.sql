-- =============================================
-- Service : tuso-service
-- Schema  : tuso
-- Flyway  : V002__full_schema.sql
-- Purpose : Complete production schema for Facility Registry
-- =============================================

-- Trigram extension required before GIN indexes using gin_trgm_ops
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ═══════════════════════════════════════════════════════════════════════
-- 1. Facility Register (GOFR-compatible)
-- ═══════════════════════════════════════════════════════════════════════

-- Expand the V001 facility table with full attributes
ALTER TABLE tuso.facility
    ADD COLUMN IF NOT EXISTS gofr_id         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ownership       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS level           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS parent_id       BIGINT REFERENCES tuso.facility(id),
    ADD COLUMN IF NOT EXISTS operational_status VARCHAR(30) DEFAULT 'OPERATIONAL',
    ADD COLUMN IF NOT EXISTS description     TEXT,
    ADD COLUMN IF NOT EXISTS opened_date     DATE,
    ADD COLUMN IF NOT EXISTS closed_date     DATE,
    ADD COLUMN IF NOT EXISTS close_reason    TEXT,
    ADD COLUMN IF NOT EXISTS merged_into_id  BIGINT REFERENCES tuso.facility(id),
    ADD COLUMN IF NOT EXISTS version         INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS created_by      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by      VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_facility_parent ON tuso.facility (parent_id);
CREATE INDEX IF NOT EXISTS idx_facility_gofr ON tuso.facility (gofr_id) WHERE gofr_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_facility_name_trgm ON tuso.facility USING gin (name gin_trgm_ops);

-- Facility identifiers (national code, GOFR ID, DHIS2 UID, etc.)
CREATE TABLE tuso.facility_identifier (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    system          VARCHAR(100)    NOT NULL,
    value           VARCHAR(255)    NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (facility_id, system, value)
);

CREATE INDEX idx_fi_system_value ON tuso.facility_identifier (system, value);

-- Facility contacts
CREATE TABLE tuso.facility_contact (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    contact_type    VARCHAR(30)     NOT NULL,
    name            VARCHAR(255),
    phone           VARCHAR(50),
    email           VARCHAR(255),
    role            VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Facility geo details (extended beyond lat/lng in facility table)
CREATE TABLE tuso.facility_geo (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id) UNIQUE,
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    province        VARCHAR(100),
    district        VARCHAR(100),
    ward            VARCHAR(100),
    postal_code     VARCHAR(20),
    country         VARCHAR(3)      NOT NULL DEFAULT 'ZWE',
    altitude_m      NUMERIC(8,2),
    catchment_area  TEXT,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Facility capabilities (services offered, departments, skill sets, operating hours)
CREATE TABLE tuso.facility_capability (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    capability_code VARCHAR(100)    NOT NULL,
    capability_type VARCHAR(50)     NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    zibo_validated  BOOLEAN         NOT NULL DEFAULT false,
    active          BOOLEAN         NOT NULL DEFAULT true,
    operating_hours JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_fc_facility ON tuso.facility_capability (facility_id, active);
CREATE INDEX idx_fc_code ON tuso.facility_capability (capability_code, capability_type);

-- Facility readiness attributes
CREATE TABLE tuso.facility_readiness (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id) UNIQUE,
    connectivity    VARCHAR(30),
    power_source    VARCHAR(50),
    power_backup    BOOLEAN         NOT NULL DEFAULT false,
    device_count    INT             NOT NULL DEFAULT 0,
    ehr_ready       BOOLEAN         NOT NULL DEFAULT false,
    compliance_flags JSONB,
    assessed_at     TIMESTAMPTZ,
    assessed_by     VARCHAR(255),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Facility history/versioning
CREATE TABLE tuso.facility_history (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    change_type     VARCHAR(30)     NOT NULL,
    field_name      VARCHAR(100),
    old_value       TEXT,
    new_value       TEXT,
    changed_by      VARCHAR(255)    NOT NULL,
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reason          TEXT
);

CREATE INDEX idx_fh_facility ON tuso.facility_history (facility_id, changed_at DESC);

-- Facility relationships (referral network hints)
CREATE TABLE tuso.facility_relationship (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    source_id       BIGINT          NOT NULL REFERENCES tuso.facility(id),
    target_id       BIGINT          NOT NULL REFERENCES tuso.facility(id),
    relationship    VARCHAR(50)     NOT NULL,
    metadata        JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, source_id, target_id, relationship)
);

-- ═══════════════════════════════════════════════════════════════════════
-- 2. Workspaces & Operational Context
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.workspace (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    workspace_type  VARCHAR(50)     NOT NULL,
    zibo_validated  BOOLEAN         NOT NULL DEFAULT false,
    description     TEXT,
    queue_config    JSONB,
    default_panels  JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

CREATE INDEX idx_ws_facility ON tuso.workspace (facility_id, active);
CREATE INDEX idx_ws_type ON tuso.workspace (tenant_id, workspace_type, active);

-- Workspace eligibility rules (which cadres can work here)
CREATE TABLE tuso.workspace_rule (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    UUID            NOT NULL REFERENCES tuso.workspace(id),
    rule_type       VARCHAR(30)     NOT NULL,
    actor_type      VARCHAR(50),
    role            VARCHAR(100),
    privilege       VARCHAR(100),
    required        BOOLEAN         NOT NULL DEFAULT true,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_wr_workspace ON tuso.workspace_rule (workspace_id);

-- Workspace dashboards
CREATE TABLE tuso.workspace_dashboard (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    UUID            NOT NULL REFERENCES tuso.workspace(id),
    panel_name      VARCHAR(100)    NOT NULL,
    panel_type      VARCHAR(50)     NOT NULL,
    config          JSONB,
    sort_order      INT             NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Shift context records
CREATE TABLE tuso.shift (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    workspace_id    UUID            NOT NULL REFERENCES tuso.workspace(id),
    provider_id     VARCHAR(255)    NOT NULL,
    provider_type   VARCHAR(50)     NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_shift_active ON tuso.shift (tenant_id, provider_id, status)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_shift_facility ON tuso.shift (facility_id, started_at DESC);

-- Workspace override log (manager overrides with mandatory reason)
CREATE TABLE tuso.workspace_override_log (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    UUID            NOT NULL REFERENCES tuso.workspace(id),
    tenant_id       UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    override_type   VARCHAR(50)     NOT NULL,
    reason          TEXT            NOT NULL,
    old_value       JSONB,
    new_value       JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_wol_workspace ON tuso.workspace_override_log (workspace_id, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════
-- 3. Resource Catalog + Bookings
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.resource (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    resource_type   VARCHAR(50)     NOT NULL,
    capacity        INT             NOT NULL DEFAULT 1,
    status          VARCHAR(30)     NOT NULL DEFAULT 'AVAILABLE',
    maintenance     BOOLEAN         NOT NULL DEFAULT false,
    location_desc   VARCHAR(255),
    metadata        JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_res_facility ON tuso.resource (facility_id, resource_type, active);

-- Resource calendar (recurring schedules)
CREATE TABLE tuso.resource_calendar (
    id              BIGSERIAL       PRIMARY KEY,
    resource_id     UUID            NOT NULL REFERENCES tuso.resource(id),
    day_of_week     INT             NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    slot_duration_minutes INT       NOT NULL DEFAULT 30,
    active          BOOLEAN         NOT NULL DEFAULT true,
    effective_from  DATE            NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rc_resource ON tuso.resource_calendar (resource_id, active);

-- Bookings
CREATE TABLE tuso.booking (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id     UUID            NOT NULL REFERENCES tuso.resource(id),
    tenant_id       UUID            NOT NULL,
    facility_id     BIGINT          NOT NULL,
    booked_by       VARCHAR(255)    NOT NULL,
    subject_ref     VARCHAR(255),
    purpose         VARCHAR(100),
    start_time      TIMESTAMPTZ     NOT NULL,
    end_time        TIMESTAMPTZ     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED',
    notes           TEXT,
    cancelled_by    VARCHAR(255),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_bk_resource_time ON tuso.booking (resource_id, start_time, end_time)
    WHERE status != 'CANCELLED';
CREATE INDEX idx_bk_facility_time ON tuso.booking (facility_id, start_time, end_time);

-- ═══════════════════════════════════════════════════════════════════════
-- 4. Facility Configuration Registry
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.facility_config_version (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    version         INT             NOT NULL,
    config_data     JSONB           NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    rollback_from   INT,
    UNIQUE (facility_id, version)
);

CREATE INDEX idx_fcv_active ON tuso.facility_config_version (facility_id, active, version DESC);

-- Tenant-level default config
CREATE TABLE tuso.tenant_config_default (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL UNIQUE,
    config_data     JSONB           NOT NULL,
    updated_by      VARCHAR(255)    NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Workspace-level config overrides
CREATE TABLE tuso.workspace_config_override (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    UUID            NOT NULL REFERENCES tuso.workspace(id),
    tenant_id       UUID            NOT NULL,
    config_data     JSONB           NOT NULL,
    updated_by      VARCHAR(255)    NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (workspace_id)
);

-- Config audit trail
CREATE TABLE tuso.config_audit (
    id              BIGSERIAL       PRIMARY KEY,
    entity_type     VARCHAR(30)     NOT NULL,
    entity_id       VARCHAR(255)    NOT NULL,
    tenant_id       UUID            NOT NULL,
    action          VARCHAR(20)     NOT NULL,
    old_config      JSONB,
    new_config      JSONB,
    actor_id        VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ca_entity ON tuso.config_audit (entity_type, entity_id, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════
-- 5. Occupancy Snapshots (from PCT admissions feed)
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.occupancy_snapshot (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    ward            VARCHAR(100),
    total_beds      INT             NOT NULL DEFAULT 0,
    occupied_beds   INT             NOT NULL DEFAULT 0,
    available_beds  INT             NOT NULL DEFAULT 0,
    snapshot_time   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    source          VARCHAR(50)     NOT NULL DEFAULT 'PCT'
);

CREATE INDEX idx_os_facility ON tuso.occupancy_snapshot (facility_id, snapshot_time DESC);

-- ═══════════════════════════════════════════════════════════════════════
-- 6. Control Tower — Telemetry & Alerts
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.telemetry_event (
    id              BIGSERIAL       PRIMARY KEY,
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    source          VARCHAR(30)     NOT NULL,
    metric_type     VARCHAR(50)     NOT NULL,
    metric_value    NUMERIC(12,2)   NOT NULL,
    unit            VARCHAR(20),
    metadata        JSONB,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_te_facility_source ON tuso.telemetry_event (facility_id, source, recorded_at DESC);
CREATE INDEX idx_te_type ON tuso.telemetry_event (tenant_id, metric_type, recorded_at DESC);

CREATE TABLE tuso.alert (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id     BIGINT          NOT NULL REFERENCES tuso.facility(id),
    tenant_id       UUID            NOT NULL,
    alert_type      VARCHAR(50)     NOT NULL,
    severity        VARCHAR(20)     NOT NULL DEFAULT 'WARNING',
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    metric_type     VARCHAR(50),
    metric_value    NUMERIC(12,2),
    threshold       NUMERIC(12,2),
    status          VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_open ON tuso.alert (tenant_id, status, created_at DESC)
    WHERE status = 'OPEN';
CREATE INDEX idx_alert_facility ON tuso.alert (facility_id, status, created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════
-- 7. Event Outbox
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished
    ON tuso.event_outbox (created_at)
    WHERE published_at IS NULL;

-- ═══════════════════════════════════════════════════════════════════════
-- 8. GOFR Sync Tracking
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE tuso.gofr_sync_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    sync_type       VARCHAR(30)     NOT NULL,
    direction       VARCHAR(10)     NOT NULL,
    facilities_processed INT        NOT NULL DEFAULT 0,
    facilities_created   INT        NOT NULL DEFAULT 0,
    facilities_updated   INT        NOT NULL DEFAULT 0,
    errors          INT             NOT NULL DEFAULT 0,
    error_details   JSONB,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(20)     NOT NULL DEFAULT 'RUNNING'
);
