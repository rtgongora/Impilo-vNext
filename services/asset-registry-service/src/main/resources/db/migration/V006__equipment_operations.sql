-- ============================================================================
-- asset-registry-service V006 — Equipment operations lifecycle (WS#5)
--
-- Extends the EXISTING asr_equipment / asr_assets registry with the operational
-- truth layer: criticality + readiness requirements, custody transfers + handover,
-- maintenance tasks, calibration records, fault reports, lifecycle events, IoT
-- alerts (derived — never fabricated), service-readiness profiles, deployment kits,
-- and asset audits.
--
-- Boundary doctrine (see docs/assets/asset-device-iot-discovery.md):
--   * Stock / spare-parts → inventory-service (Dura). We store only a REFERENCE.
--   * Facility / site      → Tuso / Indawo. We store only a reference id.
--   * Workforce (technician/custodian) → Vashandi. Reference id only.
--   * Clinical observation → Butano. Operational status only here.
--   * Safety incident      → Rito. We store a link reference only.
-- ============================================================================

-- ── Equipment criticality + readiness flags (extend asr_equipment in place) ──
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS criticality        VARCHAR(16)  NOT NULL DEFAULT 'STANDARD';
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS asset_class        VARCHAR(32)  NOT NULL DEFAULT 'INFRASTRUCTURE';
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS regulated          BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS custodian_ref      VARCHAR(255);
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS tag                VARCHAR(255);
ALTER TABLE asr_equipment ADD COLUMN IF NOT EXISTS clinical_device_type VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_asr_equipment_criticality ON asr_equipment (criticality);
CREATE INDEX IF NOT EXISTS idx_asr_equipment_tag ON asr_equipment (tag) WHERE tag IS NOT NULL;

-- ── Lifecycle event log (append-only audit of every meaningful equipment action) ─
CREATE TABLE asr_equipment_lifecycle_event (
    event_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    event_type      VARCHAR(48)     NOT NULL,   -- REGISTERED|ASSIGNED|TRANSFERRED|RECEIVED|STATUS_CHANGED|FAULT_REPORTED|MARKED_UNSAFE|MAINTENANCE_OPENED|MAINTENANCE_CLOSED|CALIBRATED|RETURNED_TO_SERVICE|RETIRED|DEPLOYED|AUDITED|IOT_ALERT
    from_value      VARCHAR(255),
    to_value        VARCHAR(255),
    actor_ref       VARCHAR(255),
    note            TEXT,
    metadata_json   JSONB,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_asr_eq_event_equipment ON asr_equipment_lifecycle_event (equipment_id, occurred_at DESC);
CREATE INDEX idx_asr_eq_event_tenant ON asr_equipment_lifecycle_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_asr_eq_event_type ON asr_equipment_lifecycle_event (event_type);

-- ── Custody transfer + handover/receipt ──────────────────────────────────────
CREATE TABLE asr_equipment_transfer (
    transfer_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    from_facility   VARCHAR(255),
    to_facility     VARCHAR(255)    NOT NULL,
    from_location   VARCHAR(512),
    to_location     VARCHAR(512),
    to_custodian_ref VARCHAR(255),
    reason          TEXT,
    status          VARCHAR(24)     NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|RECEIVED|REJECTED|CANCELLED
    requires_approval BOOLEAN       NOT NULL DEFAULT FALSE,
    initiated_by    VARCHAR(255),
    approved_by     VARCHAR(255),
    received_by     VARCHAR(255),
    initiated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    received_at     TIMESTAMPTZ,
    metadata_json   JSONB
);
CREATE INDEX idx_asr_transfer_equipment ON asr_equipment_transfer (equipment_id, initiated_at DESC);
CREATE INDEX idx_asr_transfer_status ON asr_equipment_transfer (tenant_id, status);

-- ── Maintenance tasks (preventive + corrective + inspection) ─────────────────
CREATE TABLE asr_maintenance_task (
    task_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    task_type       VARCHAR(24)     NOT NULL,   -- PREVENTIVE|CORRECTIVE|INSPECTION
    title           VARCHAR(512)    NOT NULL,
    description     TEXT,
    priority        VARCHAR(16)     NOT NULL DEFAULT 'NORMAL',  -- LOW|NORMAL|HIGH|CRITICAL
    status          VARCHAR(24)     NOT NULL DEFAULT 'OPEN',    -- OPEN|ASSIGNED|IN_PROGRESS|AWAITING_PARTS|COMPLETED|CANCELLED
    technician_ref  VARCHAR(255),   -- Vashandi reference (not owned)
    spare_part_request_ref VARCHAR(255),  -- inventory-service/Oros reference (not owned)
    fault_report_id UUID,
    due_date        DATE,
    downtime_minutes INT,
    service_report  TEXT,
    opened_by       VARCHAR(255),
    completed_by    VARCHAR(255),
    opened_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    metadata_json   JSONB
);
CREATE INDEX idx_asr_mt_equipment ON asr_maintenance_task (equipment_id, opened_at DESC);
CREATE INDEX idx_asr_mt_status ON asr_maintenance_task (tenant_id, status);
CREATE INDEX idx_asr_mt_due ON asr_maintenance_task (due_date) WHERE status NOT IN ('COMPLETED','CANCELLED');

-- ── Calibration records ──────────────────────────────────────────────────────
CREATE TABLE asr_calibration_record (
    calibration_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    outcome         VARCHAR(16)     NOT NULL,   -- PASS|FAIL|ADJUSTED
    performed_by    VARCHAR(255),
    performed_on    DATE            NOT NULL,
    next_due        DATE,
    certificate_ref VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    metadata_json   JSONB
);
CREATE INDEX idx_asr_cal_equipment ON asr_calibration_record (equipment_id, performed_on DESC);
CREATE INDEX idx_asr_cal_due ON asr_calibration_record (tenant_id, next_due);

-- ── Fault reports (route safety → Rito link; otherwise spawn maintenance task) ─
CREATE TABLE asr_fault_report (
    fault_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    severity        VARCHAR(16)     NOT NULL DEFAULT 'MINOR',  -- MINOR|MAJOR|SAFETY
    summary         VARCHAR(512)    NOT NULL,
    detail          TEXT,
    reported_by     VARCHAR(255),
    safety_concern  BOOLEAN         NOT NULL DEFAULT FALSE,
    rito_case_ref   VARCHAR(255),   -- link to Rito (not owned)
    maintenance_task_id UUID,
    status          VARCHAR(24)     NOT NULL DEFAULT 'OPEN',   -- OPEN|TRIAGED|LINKED|RESOLVED
    reported_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    metadata_json   JSONB
);
CREATE INDEX idx_asr_fault_equipment ON asr_fault_report (equipment_id, reported_at DESC);
CREATE INDEX idx_asr_fault_status ON asr_fault_report (tenant_id, status);

-- ── IoT alerts (DERIVED from real telemetry/heartbeat — never fabricated) ────
CREATE TABLE asr_iot_alert (
    alert_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    equipment_id    UUID            REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    device_id       VARCHAR(255)    NOT NULL,
    alert_type      VARCHAR(32)     NOT NULL,   -- OFFLINE|THRESHOLD_BREACH|BATTERY_LOW|TAMPERED|LOCATION_CHANGED
    metric_type     VARCHAR(128),
    observed_value  DOUBLE PRECISION,
    threshold_value DOUBLE PRECISION,
    severity        VARCHAR(16)     NOT NULL DEFAULT 'WARN',  -- INFO|WARN|CRITICAL
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|RESOLVED
    khuluma_requested BOOLEAN       NOT NULL DEFAULT FALSE,
    detail          TEXT,
    observed_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    resolved_by     VARCHAR(255)
);
CREATE INDEX idx_asr_alert_equipment ON asr_iot_alert (equipment_id, observed_at DESC);
CREATE INDEX idx_asr_alert_device ON asr_iot_alert (device_id, observed_at DESC);
CREATE INDEX idx_asr_alert_active ON asr_iot_alert (tenant_id, status) WHERE status = 'ACTIVE';

-- ── Service-readiness: required-asset profile per service point ───────────────
CREATE TABLE asr_readiness_profile (
    profile_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_ref    VARCHAR(255)    NOT NULL,
    service_point   VARCHAR(255)    NOT NULL,   -- e.g. THEATRE-1, LAB, MATERNITY, AMBULANCE-A
    name            VARCHAR(512)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    metadata_json   JSONB,
    CONSTRAINT uq_asr_readiness UNIQUE (tenant_id, facility_ref, service_point)
);
CREATE INDEX idx_asr_readiness_facility ON asr_readiness_profile (tenant_id, facility_ref);

CREATE TABLE asr_readiness_requirement (
    requirement_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id      UUID            NOT NULL REFERENCES asr_readiness_profile(profile_id) ON DELETE CASCADE,
    equipment_type  VARCHAR(64)     NOT NULL,
    min_count       INT             NOT NULL DEFAULT 1,
    must_be_critical BOOLEAN        NOT NULL DEFAULT FALSE,
    note            VARCHAR(512)
);
CREATE INDEX idx_asr_req_profile ON asr_readiness_requirement (profile_id);

-- ── Deployment kits (emergency/outreach/mobile) ──────────────────────────────
CREATE TABLE asr_deployment_kit (
    kit_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    name            VARCHAR(512)    NOT NULL,
    deployment_ref  VARCHAR(255),   -- outreach team / programme / vehicle reference
    location_ref    VARCHAR(255),   -- Ndila reference (not owned)
    status          VARCHAR(24)     NOT NULL DEFAULT 'PREPARED',  -- PREPARED|DEPLOYED|RETURNED|RECONCILED
    custodian_ref   VARCHAR(255),
    deployed_at     TIMESTAMPTZ,
    returned_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    metadata_json   JSONB
);
CREATE INDEX idx_asr_kit_status ON asr_deployment_kit (tenant_id, status);

CREATE TABLE asr_deployment_kit_item (
    item_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    kit_id          UUID            NOT NULL REFERENCES asr_deployment_kit(kit_id) ON DELETE CASCADE,
    equipment_id    UUID            NOT NULL REFERENCES asr_equipment(equipment_id) ON DELETE CASCADE,
    return_condition VARCHAR(24),   -- OK|DAMAGED|LOST
    return_note     VARCHAR(512)
);
CREATE INDEX idx_asr_kit_item_kit ON asr_deployment_kit_item (kit_id);

-- ── Asset audit / verification ───────────────────────────────────────────────
CREATE TABLE asr_asset_audit (
    audit_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_ref    VARCHAR(255)    NOT NULL,
    name            VARCHAR(512)    NOT NULL,
    status          VARCHAR(24)     NOT NULL DEFAULT 'OPEN',  -- OPEN|COMPLETED
    conducted_by    VARCHAR(255),
    opened_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    metadata_json   JSONB
);
CREATE INDEX idx_asr_audit_facility ON asr_asset_audit (tenant_id, facility_ref);

CREATE TABLE asr_asset_audit_item (
    audit_item_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id        UUID            NOT NULL REFERENCES asr_asset_audit(audit_id) ON DELETE CASCADE,
    equipment_id    UUID            REFERENCES asr_equipment(equipment_id) ON DELETE SET NULL,
    expected        BOOLEAN         NOT NULL DEFAULT TRUE,
    confirmed       BOOLEAN         NOT NULL DEFAULT FALSE,
    exception_code  VARCHAR(32),    -- MISSING|UNEXPECTED|DAMAGED|WRONG_LOCATION
    note            VARCHAR(512)
);
CREATE INDEX idx_asr_audit_item_audit ON asr_asset_audit_item (audit_id);
