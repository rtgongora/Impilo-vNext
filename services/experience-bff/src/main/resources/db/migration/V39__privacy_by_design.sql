-- ============================================================================
-- V39: Privacy-by-design infrastructure
--
-- 1. PII access audit log — records every access to PII fields with
--    actor, purpose, subject, and what fields were accessed
-- 2. Privacy preferences per user — controls PII display level
-- 3. Data retention schedule metadata
-- ============================================================================

-- PII access audit — immutable append-only log
CREATE TABLE pii_access_log (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,     -- Who accessed PII (Health ID)
    actor_type      VARCHAR(30),               -- PROVIDER | OPERATOR | CITIZEN | SYSTEM
    subject_id      VARCHAR(255) NOT NULL,     -- Whose PII was accessed (patient/user ID)
    subject_type    VARCHAR(30)  NOT NULL DEFAULT 'PATIENT',  -- PATIENT | USER | PROVIDER
    action          VARCHAR(30)  NOT NULL,     -- VIEW | SEARCH | EXPORT | PRINT | COPY
    fields_accessed TEXT[],                    -- Array of field names: {name, dob, national_id, phone, email}
    purpose_of_use  VARCHAR(50),              -- TREATMENT | PAYMENT | OPERATIONS | PUBLIC_HEALTH | EMERGENCY
    access_channel  VARCHAR(20),              -- WEB | APP | API | KIOSK
    privacy_level   VARCHAR(10),              -- FULL | PARTIAL | MINIMAL — what level was used for display
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    facility_id     VARCHAR(255),
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pii_access_actor ON pii_access_log (tenant_id, actor_id, occurred_at DESC);
CREATE INDEX idx_pii_access_subject ON pii_access_log (tenant_id, subject_id, occurred_at DESC);
CREATE INDEX idx_pii_access_time ON pii_access_log (occurred_at DESC);

-- Privacy display preferences per user
CREATE TABLE privacy_display_preference (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    default_level   VARCHAR(10)  NOT NULL DEFAULT 'PARTIAL',  -- FULL | PARTIAL | MINIMAL
    auto_lock_minutes INT        NOT NULL DEFAULT 5,          -- Minutes of inactivity before auto-lock
    screen_share_mode BOOLEAN    NOT NULL DEFAULT TRUE,       -- Auto-MINIMAL when screen sharing detected
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_privacy_pref UNIQUE (tenant_id, user_id)
);

-- Data retention schedule metadata — tracks what data types have what retention periods
CREATE TABLE data_retention_schedule (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    data_category   VARCHAR(50)  NOT NULL,   -- PATIENT_PII | CLINICAL_RECORD | AUDIT_LOG | CONSENT | SESSION
    retention_days  INT          NOT NULL,   -- Number of days to retain
    legal_basis     TEXT,                    -- Legal justification for retention period
    applies_to      VARCHAR(50),            -- ACTIVE | DELETED | ALL
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_retention_schedule UNIQUE (tenant_id, data_category, applies_to)
);

-- Seed default retention schedules
INSERT INTO data_retention_schedule (id, tenant_id, data_category, retention_days, legal_basis, applies_to) VALUES
    (gen_random_uuid(), 'tenant-moh-zw', 'PATIENT_PII', 2555, 'Health records retention: 7 years per Zimbabwe health records regulations', 'ALL'),
    (gen_random_uuid(), 'tenant-moh-zw', 'CLINICAL_RECORD', 2555, 'Clinical records: 7 years minimum for continuity of care', 'ALL'),
    (gen_random_uuid(), 'tenant-moh-zw', 'AUDIT_LOG', 2555, 'Audit trail: 7 years for regulatory compliance', 'ALL'),
    (gen_random_uuid(), 'tenant-moh-zw', 'CONSENT', 2555, 'Consent records: retained for duration of data they govern', 'ALL'),
    (gen_random_uuid(), 'tenant-moh-zw', 'SESSION', 90, 'Session data: 90 days for security analysis', 'ALL'),
    (gen_random_uuid(), 'tenant-moh-zw', 'PII_ACCESS_LOG', 2555, 'PII access audit: 7 years for compliance and breach investigation', 'ALL')
ON CONFLICT DO NOTHING;
