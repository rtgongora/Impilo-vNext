-- =============================================================================
-- V25: Announcements / Communication Noticeboard
-- =============================================================================
-- Provider noticeboard with categories, priorities, pinning, expiry,
-- acknowledgment tracking, and clinical paging.

-- ── Announcements ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS announcements (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(255) NOT NULL,
    title                   VARCHAR(500) NOT NULL,
    content                 TEXT NOT NULL,
    category                VARCHAR(50) NOT NULL DEFAULT 'general',
    priority                VARCHAR(20) NOT NULL DEFAULT 'normal',
    target_departments      TEXT[],
    target_roles            TEXT[],
    published_by            VARCHAR(255),
    published_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ,
    is_pinned               BOOLEAN NOT NULL DEFAULT FALSE,
    requires_acknowledgment BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_announcements_tenant ON announcements (tenant_id, published_at DESC);
CREATE INDEX idx_announcements_pinned ON announcements (tenant_id, is_pinned, published_at DESC);
CREATE INDEX idx_announcements_category ON announcements (tenant_id, category);
CREATE INDEX idx_announcements_active ON announcements (tenant_id, status)
    WHERE status = 'ACTIVE';

-- ── Announcement Acknowledgments ────────────────────────────────────
CREATE TABLE IF NOT EXISTS announcement_acknowledgments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    announcement_id UUID NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id         VARCHAR(255) NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_announcement_ack UNIQUE (announcement_id, user_id)
);

-- ── Clinical Pages (urgent notifications) ───────────────────────────
CREATE TABLE IF NOT EXISTS clinical_pages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    sender_id       VARCHAR(255) NOT NULL,
    sender_name     VARCHAR(255),
    recipient_id    VARCHAR(255) NOT NULL,
    recipient_name  VARCHAR(255),
    urgency         VARCHAR(20) NOT NULL DEFAULT 'normal',
    message         TEXT NOT NULL,
    callback_number VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ,
    responded_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pages_recipient ON clinical_pages (tenant_id, recipient_id, sent_at DESC);
CREATE INDEX idx_pages_sender ON clinical_pages (tenant_id, sender_id, sent_at DESC);

-- ── Clinical Messages (secure messaging) ────────────────────────────
CREATE TABLE IF NOT EXISTS clinical_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    channel_id      UUID NOT NULL,
    sender_id       VARCHAR(255) NOT NULL,
    sender_name     VARCHAR(255),
    content         TEXT NOT NULL,
    message_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS message_channels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    channel_type    VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    participants    TEXT[] NOT NULL,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_channel ON clinical_messages (channel_id, created_at DESC);
CREATE INDEX idx_channels_tenant ON message_channels (tenant_id);

-- ── Seed Announcements ──────────────────────────────────────────────
INSERT INTO announcements (id, tenant_id, title, content, category, priority, is_pinned, requires_acknowledgment, published_by, published_at)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'System Maintenance Window', 'Scheduled maintenance on Saturday 06:00-08:00 CAT. All clinical systems will be briefly unavailable. Please ensure all encounters are saved before this window.', 'general', 'high', true, true, 'System Admin', now() - interval '1 day'),
(gen_random_uuid(), 'tenant-moh-zw', 'Updated Malaria Treatment Protocol', 'Effective immediately: all confirmed malaria cases should follow the revised ACT dosing guidelines posted in the clinical resources section. Please review before your next shift.', 'policy', 'urgent', true, true, 'Chief Medical Officer', now() - interval '3 hours'),
(gen_random_uuid(), 'tenant-moh-zw', 'New EMR Training Sessions', 'Weekly training sessions available every Wednesday 14:00-15:00 in Conference Room B. Topics include: EHR documentation, order entry, and discharge workflows.', 'training', 'normal', false, false, 'Training Coordinator', now() - interval '2 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'Blood Bank Shortage Alert', 'Critical shortage of O-negative blood supply. Please consider ordering alternatives where clinically appropriate. Contact Blood Bank ext. 4401 for availability.', 'emergency', 'urgent', true, false, 'Blood Bank', now() - interval '6 hours'),
(gen_random_uuid(), 'tenant-moh-zw', 'Night Shift Coverage Needed', 'Additional nursing coverage needed for Medical Ward 3 this coming Friday night shift (19:00-07:00). Please contact Nurse Manager if available.', 'shift', 'high', false, false, 'Nurse Manager', now() - interval '1 day'),
(gen_random_uuid(), 'tenant-moh-zw', 'Pharmacy Formulary Update', 'New medications added to formulary: Empagliflozin 10mg, Dapagliflozin 5mg. Removed: Glibenclamide (phased out per WHO guidance).', 'policy', 'normal', false, false, 'Chief Pharmacist', now() - interval '4 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'Hand Hygiene Compliance Audit', 'Monthly hand hygiene audit results: 87% compliance. Target is 95%. Department heads please reinforce protocols with staff.', 'general', 'normal', false, false, 'Infection Control', now() - interval '5 days');
