-- V23: Omnichannel infrastructure tables.
-- Supports callback queue, channel configurations, disclosure rules,
-- SMS journeys, USSD menus, IVR flows, and AI agent settings.

-- ── Callback Queue ───────────────────────────────────────────────
CREATE TABLE omni_callback_queue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    channel         VARCHAR(30) NOT NULL DEFAULT 'PHONE',
    caller_id       VARCHAR(255) NOT NULL,
    caller_name     VARCHAR(255),
    reason          TEXT,
    priority        VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assigned_to     VARCHAR(255),
    scheduled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_callbacks_tenant ON omni_callback_queue (tenant_id, status);

COMMENT ON COLUMN omni_callback_queue.channel IS 'PHONE, SMS, WHATSAPP, EMAIL';
COMMENT ON COLUMN omni_callback_queue.status IS 'PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, ESCALATED, CANCELLED';
COMMENT ON COLUMN omni_callback_queue.priority IS 'URGENT, HIGH, NORMAL, LOW';

-- ── Channel Configurations ───────────────────────────────────────
CREATE TABLE omni_channel_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    channel_type    VARCHAR(30) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_channel_configs_tenant ON omni_channel_configs (tenant_id, channel_type);

COMMENT ON COLUMN omni_channel_configs.channel_type IS 'SMS, USSD, IVR, WHATSAPP, EMAIL, WEBCHAT';

-- ── Disclosure Rules ─────────────────────────────────────────────
CREATE TABLE omni_disclosure_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    channel_type    VARCHAR(30) NOT NULL,
    data_category   VARCHAR(50) NOT NULL,
    disclosure_level VARCHAR(30) NOT NULL DEFAULT 'MINIMAL',
    conditions      JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_disclosure_rules_tenant ON omni_disclosure_rules (tenant_id, channel_type);

COMMENT ON COLUMN omni_disclosure_rules.data_category IS 'DEMOGRAPHICS, CLINICAL, FINANCIAL, CONTACT, LOCATION';
COMMENT ON COLUMN omni_disclosure_rules.disclosure_level IS 'FULL, SUMMARY, MINIMAL, NONE';

-- ── SMS Journey Definitions ─────────────────────────────────────
CREATE TABLE omni_sms_journeys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    trigger_event   VARCHAR(100) NOT NULL,
    message_template TEXT NOT NULL,
    schedule_cron   VARCHAR(100),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sent_count      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_journeys_tenant ON omni_sms_journeys (tenant_id);

-- ── USSD Menu Definitions ────────────────────────────────────────
CREATE TABLE omni_ussd_menus (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    short_code      VARCHAR(20) NOT NULL,
    menu_tree       JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── IVR Flow Definitions ─────────────────────────────────────────
CREATE TABLE omni_ivr_flows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    phone_number    VARCHAR(30),
    flow_definition JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
